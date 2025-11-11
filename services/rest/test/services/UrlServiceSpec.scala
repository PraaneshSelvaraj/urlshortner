package services

import dtos.UrlDto
import models.Url
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import repositories.UrlRepo
import example.urlshortner.notification.grpc.{
  GetNotificationsResponse,
  NotificationReply,
  NotificationRequest,
  NotificationServiceClient,
  NotificationType,
  GetNotificationsRequest
}
import com.google.protobuf.empty.Empty
import com.typesafe.config.ConfigFactory
import exceptions.TresholdReachedException
import org.scalatest.time.{Seconds, Span, Milliseconds}
import java.sql.Timestamp
import java.time.{Instant, Duration}
import scala.concurrent.{ExecutionContext, Future}
import exceptions.UrlExpiredException

class UrlServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockUrlRepo: UrlRepo = mock[UrlRepo]
  val mockNotificationServiceClient: NotificationServiceClient = mock[NotificationServiceClient]
  val testConfig: Configuration = Configuration(
    ConfigFactory.parseString("""
      bannedHosts = []
      urlExpirationHours = 24
      notification.treshold = 5
    """)
  )

  val urlService = new UrlService(mockUrlRepo, mockNotificationServiceClient, testConfig)

  val sampleUrlDto = UrlDto("https://www.example.com")
  val sampleUrl = Url(
    id = 1L,
    user_id = 1L,
    short_code = "abcdefg",
    long_url = "https://www.example.com",
    clicks = 0,
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now()),
    expires_at = Timestamp.from(Instant.now().plus(Duration.ofHours(1)))
  )

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(2, Seconds),
    interval = Span(10, Milliseconds)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUrlRepo, mockNotificationServiceClient)
  }

  "UrlService" should {

    "add a new URL and send a notification" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]())).thenReturn(Future.successful(None))
      when(mockUrlRepo.addUrl(any[Url]())).thenReturn(Future.successful(sampleUrl))
      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]())).thenReturn(
        Future.successful(NotificationReply(success = true, message = "Notification sent"))
      )

      val result = urlService.addUrl(sampleUrlDto, 1)

      whenReady(result) { addedUrl =>
        addedUrl.long_url mustBe sampleUrlDto.url
        verify(mockUrlRepo).addUrl(any[Url]())
        verify(mockNotificationServiceClient).notifyMethod(any[NotificationRequest]())
      }
    }

    "redirect and send notification" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]()))
        .thenReturn(Future.successful(Some(sampleUrl)))
      when(mockUrlRepo.incrementUrlCount(any[String]())).thenReturn(Future.successful(1))
      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]())).thenReturn(
        Future.successful(NotificationReply(success = true, message = "Notification sent"))
      )

      val result = urlService.redirect(sampleUrl.short_code)

      whenReady(result) { redirectedUrl =>
        redirectedUrl mustBe sampleUrl
        verify(mockUrlRepo).incrementUrlCount(sampleUrl.short_code)
        verify(mockNotificationServiceClient, never).notifyMethod(any[NotificationRequest]())
      }
    }

    "fail to redirect due to treshold" in {
      val treshold = 5
      when(mockUrlRepo.getUrlByShortcode(any[String]()))
        .thenReturn(Future.successful(Some(sampleUrl)))
      when(mockUrlRepo.incrementUrlCount(any[String]())).thenReturn(Future.successful(treshold + 1))
      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]())).thenReturn(
        Future.successful(NotificationReply(success = true, message = "Notification sent"))
      )

      val result = urlService.redirect(sampleUrl.short_code)

      whenReady(result.failed) { ex =>
        ex mustBe a[TresholdReachedException]
        ex.getMessage mustBe "Treshold Reached for the url"
      }

    }

    "fail to redirect due to expiration" in {
      val url = Url(
        id = 1L,
        user_id = 1L,
        short_code = "abcdefg",
        long_url = "https://www.example.com",
        clicks = 0,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now()),
        expires_at = Timestamp.from(Instant.now().minus(Duration.ofHours(1)))
      )

      when(mockUrlRepo.getUrlByShortcode(any[String]()))
        .thenReturn(Future.successful(Some(url)))

      val result = urlService.redirect(url.short_code)

      whenReady(result.failed) { ex =>
        ex mustBe a[UrlExpiredException]
        ex.getMessage mustBe "Url Expired"
      }
    }

    "get Url by short code" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]()))
        .thenReturn(Future.successful(Some(sampleUrl)))
      val result = urlService.getUrlByShortCode(sampleUrl.short_code)

      whenReady(result) { urlOpt =>
        urlOpt mustBe Some(sampleUrl)
      }
    }

    "fail to get Url when short code not found" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]())).thenReturn(Future.successful(None))
      val result = urlService.getUrlByShortCode("notshortcode")

      whenReady(result) { urlOpt =>
        urlOpt mustBe None
      }
    }

    "get all Urls" in {
      val url1 = Url(
        id = 1L,
        user_id = 1L,
        short_code = "abcdefg",
        long_url = "https://www.example.com",
        clicks = 0,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now()),
        expires_at = Timestamp.from(Instant.now().plus(Duration.ofHours(1)))
      )

      val url2 = Url(
        id = 2L,
        user_id = 1L,
        short_code = "test",
        long_url = "https://www.youtube.com",
        clicks = 6,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now()),
        expires_at = Timestamp.from(Instant.now().plus(Duration.ofHours(1)))
      )

      val url3 = Url(
        id = 3L,
        user_id = 1L,
        short_code = "sample",
        long_url = "https://www.test.com",
        clicks = 2,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now()),
        expires_at = Timestamp.from(Instant.now().plus(Duration.ofHours(1)))
      )

      when(mockUrlRepo.getAllUrls(any[Int](), any[Int]()))
        .thenReturn(Future.successful(Seq(url1, url2, url3)))

      val result = urlService.getAllUrls()

      whenReady(result) { urls =>
        urls.length mustBe 3
        urls mustBe Seq(url1, url2, url3)
      }
    }

    "return empty seq when no Urls found" in {
      when(mockUrlRepo.getAllUrls(any[Int](), any[Int]())).thenReturn(Future.successful(Seq.empty))
      val result = urlService.getAllUrls()

      whenReady(result) { urls =>
        urls.length mustBe 0
        urls mustBe Seq.empty
      }
    }

    "delete Url" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]()))
        .thenReturn(Future.successful(Some(sampleUrl)))
      when(mockUrlRepo.deleteUrlByShortCode(any[String]())).thenReturn(Future.successful(1))

      val result = urlService.deleteUrlByShortCode(sampleUrl.short_code)

      whenReady(result) { rowsAffected =>
        rowsAffected mustBe 1
      }
    }

    "fail to delete Url when short code not found" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]())).thenReturn(Future.successful(None))
      when(mockUrlRepo.deleteUrlByShortCode(any[String]())).thenReturn(Future.successful(0))

      val result = urlService.deleteUrlByShortCode("notshortcode")

      whenReady(result.failed) { ex =>
        ex mustBe a[NoSuchElementException]
        ex.getMessage mustBe "Unable to find Url with shortCode notshortcode"
      }
      verify(mockUrlRepo, never).deleteUrlByShortCode(any[String]())
    }

    "get all notifications" in {
      val grpcNotifications = Seq(
        example.urlshortner.notification.grpc.Notification(
          id = 1,
          shortCode = Some("abc"),
          notificationType = NotificationType.NEWURL,
          message = "New URL created"
        )
      )
      val grpcResponse = GetNotificationsResponse(notifications = grpcNotifications)
      when(mockNotificationServiceClient.getNotifications(any[GetNotificationsRequest]()))
        .thenReturn(Future.successful(grpcResponse))

      val result = urlService.getNotifications()

      whenReady(result) { notifications =>
        notifications.size mustBe 1
        val notification = notifications.head
        notification.short_code mustBe Some("abc")
        notification.message mustBe "New URL created"
        notification.notificationType mustBe NotificationType.NEWURL.toString
        notification.notificationStatus mustBe "SUCCESS"
        verify(mockNotificationServiceClient).getNotifications(any[GetNotificationsRequest]())
      }
    }

  }
}
