package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import dtos.UrlDto
import models.{Notification, Url}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test._
import services.UrlService

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UrlControllerSpec extends PlaySpec with MockitoSugar with DefaultAwaitTimeout {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val stubControllerComponents: ControllerComponents = Helpers.stubControllerComponents()

  "UrlController" should {

    "add url" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)
      val urlAdded = Url(1L, "abc123", "http://example.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      when(mockService.addUrl(any[UrlDto])) thenReturn Future.successful(urlAdded)

      val request = FakeRequest(POST, "/urls").withJsonBody(Json.obj("url" -> "http://example.com"))

      val result = controller.addUrl()(request)

      status(result) mustBe CREATED
      contentAsJson(result) mustBe Json.obj(("message", "Url Created successfully"), ("data", urlAdded))
    }


    "return BadRequest when request body not json" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      val request = FakeRequest(POST, "/urls").withTextBody("not json")

      val result = controller.addUrl()(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj("message" -> "Request Body needs to be JSON")
    }

    "redirect if url present" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      val url = Url(1L, "abc123", "http://example.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      when(mockService.redirect("abc123")) thenReturn Future.successful(url)

      val request = FakeRequest(GET, "/redirect/abc123")
      val result = controller.redirectUrl("abc123")(request)

      status(result) mustBe TEMPORARY_REDIRECT
      redirectLocation(result) mustBe Some("http://example.com")
    }


    "return NotFound when shortCode not present in redirect" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      when(mockService.redirect("unknown")) thenReturn Future.failed(new NoSuchElementException("Not found"))

      val request = FakeRequest(GET, "/redirect/unknown")
      val result = controller.redirectUrl("unknown")(request)

      status(result) mustBe NOT_FOUND
    }

    "get all Urls" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      val url = Url(1L, "abc123", "http://example.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      when(mockService.getAllUrls) thenReturn Future.successful(Seq(url))

      val request = FakeRequest(GET, "/urls")
      val result = controller.getUrls(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("message" -> "List of Urls", "urls" -> Json.arr(Json.toJson(url)))
    }

    "get url by short code" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      val url = Url(1L, "abc123", "http://example.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
      when(mockService.getUrlByShortCode(any[String]())).thenReturn(Future.successful(Some(url)))

      val request = FakeRequest(GET, "/urls/abc123")
      val result = controller.getUrlByShortCode("abc123")(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj(("message", "Url with shortcode abc123"), ("data", url))
    }

    "fail to get url when short code not found" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      when(mockService.getUrlByShortCode(any[String]())).thenReturn(Future.successful(None))

      val request =  FakeRequest(GET, "/urls/notshortcode")
      val result = controller.getUrlByShortCode("notshortcode")(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(("message", "Unable to find Url with shortcode notshortcode"))
    }

    "delete a url" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      when(mockService.deleteUrlByShortCode(any[String]())).thenReturn(Future.successful(1))

      val request = FakeRequest(DELETE, "/urls/abc123")
      val result = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe NO_CONTENT
    }

    "fail to delete url when exception raised" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)
      val exec = new NoSuchElementException("Unable to find Url with shortcode abc123")

      when(mockService.deleteUrlByShortCode(any[String]())).thenReturn(Future.failed(exec))

      val request = FakeRequest(DELETE, "/urls/abc123")
      val result = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(("message", s"Unable to find Url with shortCode abc123"))
    }

    "fail to delete url due to db issue" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      when(mockService.deleteUrlByShortCode(any[String]())).thenReturn(Future.successful(0))

      val request = FakeRequest(DELETE, "/urls/abc123")
      val result = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(("message", s"Unable to find Url with shortCode abc123"))

    }

    "get all notifications" in {
      val mockService = mock[UrlService]
      val controller = new UrlController(stubControllerComponents, mockService)

      val n1 = Notification(
        id = 1L,
        short_code = "abc123",
        notificationType = "NEWURL",
        notificationStatus = "SUCCESS",
        message = "created"
      )

      val n2 = Notification(
        id = 2L,
        short_code = "abc123",
        notificationType = "TRESHOLD",
        notificationStatus = "SUCCESS",
        message = "created"
      )

      val notifications = Seq(n1, n2)
      when(mockService.getNotifications) thenReturn Future.successful(notifications)

      val request = FakeRequest(GET, "/notifications")
      val result = controller.getNotifications(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("message" -> "List of all Notifications", "notifications" -> notifications)
    }
  }
}