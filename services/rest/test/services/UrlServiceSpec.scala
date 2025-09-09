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
import example.urlshortner.notification.grpc.{GetNotificationsResponse, NotificationReply, NotificationRequest, NotificationServiceClient, NotificationType}
import com.google.protobuf.empty.Empty

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UrlServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockUrlRepo: UrlRepo = mock[UrlRepo]
  val mockNotificationServiceClient: NotificationServiceClient = mock[NotificationServiceClient]
  val mockConfig: Configuration = mock[Configuration]

  val urlService = new UrlService(mockUrlRepo, mockNotificationServiceClient, mockConfig)

  val sampleUrlDto = UrlDto("https://www.example.com")
  val sampleUrl = Url(
    id = 1L,
    short_code = "abcdefg",
    long_url = "https://www.example.com",
    clicks = 0,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUrlRepo, mockNotificationServiceClient, mockConfig)
  }

  "UrlService" should {


    "add a new URL and send a notification" in {
      when(mockUrlRepo.getUrlByShortcode(any[String]())).thenReturn(Future.successful(None))
      when(mockUrlRepo.addUrl(any[Url]())).thenReturn(Future.successful(sampleUrl))
      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]())).thenReturn(Future.successful(NotificationReply(success = true, message = "Notification sent")))

      val result = urlService.addUrl(sampleUrlDto)

      whenReady(result) { addedUrl =>
        addedUrl.long_url mustBe sampleUrlDto.url
        verify(mockUrlRepo).addUrl(any[Url]())
        verify(mockNotificationServiceClient).notifyMethod(any[NotificationRequest]())
      }
    }

      "redirect and send notification" in {
        val threshold = 5
        when(mockUrlRepo.getUrlByShortcode(any[String]())).thenReturn(Future.successful(Some(sampleUrl)))
        when(mockUrlRepo.incrementUrlCount(any[String]())).thenReturn(Future.successful(threshold + 1))
        when(mockConfig.get[Int]("notification.treshold")).thenReturn(threshold)
        when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]())).thenReturn(Future.successful(NotificationReply(success = true, message = "Notification sent")))

        val result = urlService.redirect(sampleUrl.short_code)

        whenReady(result) { redirectedUrl =>
          redirectedUrl mustBe sampleUrl
          verify(mockUrlRepo).incrementUrlCount(sampleUrl.short_code)
          verify(mockNotificationServiceClient).notifyMethod(any[NotificationRequest]())
        }
      }

    "get all notifications" in {
      val grpcNotifications = Seq(
        example.urlshortner.notification.grpc.Notification(
          id = 1,
          shortCode = "abc",
          notificationType = NotificationType.NEWURL,
          message = "New URL created"
        )
      )
      val grpcResponse = GetNotificationsResponse(notifications = grpcNotifications)
      when(mockNotificationServiceClient.getNotifications(any[Empty]())).thenReturn(Future.successful(grpcResponse))

      val result = urlService.getNotifications

      whenReady(result) { notifications =>
        notifications.size mustBe 1
        val notification = notifications.head
        notification.short_code mustBe "abc"
        notification.message mustBe "New URL created"
        notification.notificationType mustBe NotificationType.NEWURL.toString
        notification.notificationStatus mustBe "SUCCESS"
        verify(mockNotificationServiceClient).getNotifications(any[Empty]())
      }
    }

  }
}