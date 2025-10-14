package controllers

import actions.RateLimiterAction
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import dtos.UrlDto
import exceptions.{TresholdReachedException, UrlExpiredException}
import models.{Notification, Url}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{BodyParsers, ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test._
import services.{RedisService, UrlService}
import auth.AuthenticatedAction
import scala.concurrent.Future
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext
import helpers.StubAuthenticatedAction
import security.JwtUtility
import repositories.UserRepo
import org.scalatest.BeforeAndAfterEach

class UrlControllerSpec
    extends PlaySpec
    with MockitoSugar
    with DefaultAwaitTimeout
    with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val stubControllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  val mockUrlService: UrlService = mock[UrlService]
  val mockRedisService: RedisService = mock[RedisService]
  val mockJwtUtility: JwtUtility = mock[JwtUtility]
  val mockUserRepo: UserRepo = mock[UserRepo]
  val mockConfig: Configuration = mock[Configuration]

  val stubAuthenticatedAction = new StubAuthenticatedAction(
    new BodyParsers.Default(stubControllerComponents.parsers),
    mockJwtUtility,
    mockUserRepo,
    shouldAuthenticate = true,
    userRole = "USER",
    userId = 1L
  )

  val defaultBodyParser = new BodyParsers.Default()(mat)
  val mockRateLimiter = new RateLimiterAction(
    mockRedisService,
    mockConfig,
    defaultBodyParser,
    stubControllerComponents
  )(ec)

  val controller = new UrlController(
    stubControllerComponents,
    mockUrlService,
    mockRateLimiter,
    stubAuthenticatedAction
  )

  val sampleUrl = Url(
    id = 1L,
    user_id = 1L,
    short_code = "abc123",
    long_url = "https://example.com",
    clicks = 0,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now()),
    expires_at = Timestamp.from(Instant.now().plusSeconds(3600))
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUrlService, mockRedisService)
  }

  "UrlController#addUrl" should {

    "create a new URL successfully" in {
      when(mockUrlService.addUrl(any[UrlDto](), any[Long]()))
        .thenReturn(Future.successful(sampleUrl))

      val request = FakeRequest(POST, "/urls")
        .withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer valid_token")
        .withJsonBody(Json.obj("url" -> "https://example.com"))

      val result: Future[Result] = controller.addUrl()(request)

      status(result) mustBe CREATED
      (contentAsJson(result) \ "message").as[String] mustBe "Url Created successfully"
      verify(mockUrlService).addUrl(any[UrlDto](), any[Long]())
    }

    "return 400 when request body is not JSON" in {
      val request = FakeRequest(POST, "/urls")
        .withHeaders("Authorization" -> "Bearer valid_token")
        .withTextBody("not json")

      val result: Future[Result] = controller.addUrl()(request)

      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] mustBe "Request Body needs to be JSON"
    }

    "return 400 when JSON schema is invalid" in {
      val request = FakeRequest(POST, "/urls")
        .withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer valid_token")
        .withJsonBody(Json.obj("invalid" -> "data"))

      val result: Future[Result] = controller.addUrl()(request)

      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] mustBe "Invalid Request Body Schema"
    }

    "return 500 when service throws exception" in {
      when(mockUrlService.addUrl(any[UrlDto](), any[Long]()))
        .thenReturn(Future.failed(new Exception("Database error")))

      val request = FakeRequest(POST, "/urls")
        .withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer valid_token")
        .withJsonBody(Json.obj("url" -> "https://example.com"))

      val result: Future[Result] = controller.addUrl()(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] mustBe "Failed to create URL"
    }
  }

  "UrlController#redirectUrl" should {

    "redirect to long URL when short code exists" in {
      when(mockUrlService.redirect("abc123"))
        .thenReturn(Future.successful(sampleUrl))
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(GET, "/abc123")
      val result: Future[Result] = controller.redirectUrl("abc123")(request)

      status(result) mustBe TEMPORARY_REDIRECT
      redirectLocation(result) mustBe Some("https://example.com")
    }

    "return 404 when short code not found" in {
      when(mockUrlService.redirect("unknown"))
        .thenReturn(Future.failed(new NoSuchElementException("Not found")))
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(GET, "/unknown")
      val result: Future[Result] = controller.redirectUrl("unknown")(request)

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] must include(
        "URL with short code 'unknown' not found"
      )
    }

    "return 403 when threshold reached" in {
      when(mockUrlService.redirect("abc123"))
        .thenReturn(Future.failed(new TresholdReachedException()))
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(GET, "/abc123")
      val result: Future[Result] = controller.redirectUrl("abc123")(request)

      status(result) mustBe FORBIDDEN
      (contentAsJson(result) \ "success").as[Boolean] mustBe false
      (contentAsJson(result) \ "message").as[String] must include("Treshold reached")
    }

    "return 403 when URL expired" in {
      when(mockUrlService.redirect("abc123"))
        .thenReturn(Future.failed(new UrlExpiredException()))
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(GET, "/abc123")
      val result: Future[Result] = controller.redirectUrl("abc123")(request)

      status(result) mustBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] must include("Url Expired")
    }

    "return 429 when rate limit exceeded" in {
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(false))

      val request = FakeRequest(GET, "/abc123")
      val result: Future[Result] = controller.redirectUrl("abc123")(request)

      status(result) mustBe TOO_MANY_REQUESTS
      verify(mockUrlService, never).redirect(any[String]())
    }

    "return 500 when generic exception occurs" in {
      when(mockUrlService.redirect("abc123"))
        .thenReturn(Future.failed(new Exception("Service error")))
      when(mockRedisService.isAllowed(any[String](), any[Int](), any[Int]()))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(GET, "/abc123")
      val result: Future[Result] = controller.redirectUrl("abc123")(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] mustBe "Error processing redirect"
    }
  }

  "UrlController#getUrls" should {

    "return all URLs successfully" in {
      val urls = Seq(sampleUrl)
      when(mockUrlService.getAllUrls)
        .thenReturn(Future.successful(urls))

      val request = FakeRequest(GET, "/urls")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUrls(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "message").as[String] mustBe "List of Urls"
      (contentAsJson(result) \ "urls").as[Seq[Url]].size mustBe 1
    }

    "return empty list when no URLs found" in {
      when(mockUrlService.getAllUrls)
        .thenReturn(Future.successful(Seq.empty))

      val request = FakeRequest(GET, "/urls")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUrls(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "urls").as[Seq[Url]] mustBe empty
    }
  }

  "UrlController#getUrlByShortCode" should {

    "return URL when short code exists" in {
      when(mockUrlService.getUrlByShortCode("abc123"))
        .thenReturn(Future.successful(Some(sampleUrl)))

      val request = FakeRequest(GET, "/urls/abc123")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUrlByShortCode("abc123")(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "message").as[String] mustBe "Url with shortcode abc123"
      verify(mockUrlService).getUrlByShortCode("abc123")
    }

    "return 404 when short code not found" in {
      when(mockUrlService.getUrlByShortCode("notfound"))
        .thenReturn(Future.successful(None))

      val request = FakeRequest(GET, "/urls/notfound")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUrlByShortCode("notfound")(request)

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] must include(
        "Unable to find Url with shortcode notfound"
      )
    }
  }

  "UrlController#deleteUrlByShortCode" should {

    "delete URL successfully" in {
      when(mockUrlService.deleteUrlByShortCode("abc123"))
        .thenReturn(Future.successful(1))

      val request = FakeRequest(DELETE, "/urls/abc123")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe NO_CONTENT
      verify(mockUrlService).deleteUrlByShortCode("abc123")
    }

    "return 404 when URL not found (0 rows affected)" in {
      when(mockUrlService.deleteUrlByShortCode("notfound"))
        .thenReturn(Future.successful(0))

      val request = FakeRequest(DELETE, "/urls/notfound")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUrlByShortCode("notfound")(request)

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] must include(
        "Unable to find Url with shortCode notfound"
      )
    }

    "return 404 when NoSuchElementException thrown" in {
      when(mockUrlService.deleteUrlByShortCode("abc123"))
        .thenReturn(Future.failed(new NoSuchElementException("Not found")))

      val request = FakeRequest(DELETE, "/urls/abc123")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] must include(
        "Unable to find Url with shortCode abc123"
      )
    }

    "return 500 when generic exception occurs" in {
      when(mockUrlService.deleteUrlByShortCode("abc123"))
        .thenReturn(Future.failed(new Exception("Database error")))

      val request = FakeRequest(DELETE, "/urls/abc123")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUrlByShortCode("abc123")(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] mustBe "Error processing redirect"
    }
  }

  "UrlController#getNotifications" should {

    "return all notifications successfully" in {
      val notifications = Seq(
        Notification(
          id = 1L,
          short_code = Some("abc123"),
          user_id = Some(1L),
          notificationType = "NEWURL",
          notificationStatus = "SUCCESS",
          message = "URL created"
        ),
        Notification(
          id = 2L,
          short_code = Some("abc123"),
          user_id = Some(1L),
          notificationType = "TRESHOLD",
          notificationStatus = "SUCCESS",
          message = "Threshold reached"
        )
      )

      when(mockUrlService.getNotifications)
        .thenReturn(Future.successful(notifications))

      val request = FakeRequest(GET, "/notifications")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getNotifications(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "message").as[String] mustBe "List of all Notifications"
      (contentAsJson(result) \ "notifications").as[Seq[Notification]].size mustBe 2
    }

    "return empty list when no notifications found" in {
      when(mockUrlService.getNotifications)
        .thenReturn(Future.successful(Seq.empty))

      val request = FakeRequest(GET, "/notifications")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getNotifications(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "notifications").as[Seq[Notification]] mustBe empty
    }
  }
}
