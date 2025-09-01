package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.protobuf.empty.Empty
import example.urlshortner.notification.grpc._
import models.Url
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories.UrlRepo
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class UrlControllerSpec extends PlaySpec with MockitoSugar with DefaultAwaitTimeout {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val stubControllerComponents: ControllerComponents = Helpers.stubControllerComponents()

  "UrlController" should {

    "add url" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      val url = Url(1L, "abc123", "http://example.com", 0, new java.sql.Timestamp(System.currentTimeMillis()))

      when(mockRepo.getUrlByShortcode(any[String]))
        .thenReturn(Future.successful(None))

      when(mockRepo.addUrl(any[Url]))
        .thenReturn(Success(Future.successful(url)))

      when(mockNotificationClient.notifyMethod(any[NotificationRequest]))
        .thenReturn(Future.successful(NotificationReply(success = true, message = "ok")))

      val request = FakeRequest(POST, "/urls")
        .withJsonBody(Json.obj("url" -> "http://example.com"))

      val result = controller.addUrl()(request)

      status(result) mustBe CREATED
    }

    "return BadRequest when request body not json" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      val request = FakeRequest(POST, "/urls")
        .withTextBody("not json")

      val result = controller.addUrl()(request)
      status(result) mustBe BAD_REQUEST

    }

    "redirect if url present" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      val url = Url(1L, "abc123", "http://example.com", 0, new java.sql.Timestamp(System.currentTimeMillis()))

      when(mockRepo.getUrlByShortcode("abc123")).thenReturn(Future.successful(Some(url)))
      when(mockRepo.incrementUrlCount("abc123")).thenReturn(Future.successful(1))
      when(mockNotificationClient.notifyMethod(any[NotificationRequest]))
        .thenReturn(Future.successful(NotificationReply(success = true, message = "ok")))

      val request = FakeRequest(GET, "/abc123")
      val result = controller.redirectUrl("abc123")(request)

      status(result) mustBe TEMPORARY_REDIRECT
    }

    "return NotFound when shortCode not present" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      when(mockRepo.getUrlByShortcode("unknown")).thenReturn(Future.successful(None))

      val request = FakeRequest(GET, "/unknown")
      val result = controller.redirectUrl("unknown")(request)

      status(result) mustBe NOT_FOUND
    }

    "get all Urls" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      val url = Url(1L, "abc123", "http://example.com", 0, new java.sql.Timestamp(System.currentTimeMillis()))

      when(mockRepo.getAllUrls).thenReturn(Future.successful(Seq(url)))

      val request = FakeRequest(GET, "/urls")
      val result = controller.getUrls(request)

      status(result) mustBe OK
    }

    "get all notifications" in {
      val mockRepo = mock[UrlRepo]
      val mockNotificationClient = mock[NotificationServiceClient]
      val config = Configuration("notification.treshold" -> 5)

      val controller = new UrlController(
        stubControllerComponents,
        mockRepo,
        mockNotificationClient,
        config
      )

      val notification = Notification(
        id = 1L,
        shortCode = "abc123",
        notificationType = NotificationType.NEWURL,
        message = "created"
      )

      when(mockNotificationClient.getNotifications(Empty()))
        .thenReturn(Future.successful(GetNotificationsResponse(Seq(notification))))

      val request = FakeRequest(GET, "/notifications")
      val result = controller.getNotifications(request)

      status(result) mustBe OK
    }
  }
}