package routers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import com.google.protobuf.empty.Empty
import example.urlshortner.notification.grpc._
import models.Notification
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import scala.concurrent.{ExecutionContext, Future}
import java.sql.Timestamp
import java.time.Instant
import repositories.NotificationRepo
import models.NotificationDTO

class NotificationRouterSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "NotificationRouter" should {

    "add a notification" in {
      val mockRepo = mock[NotificationRepo]
      val router = new NotificationRouter(mat, system, mockRepo)

      val request = NotificationRequest(
        shortCode = Some("abc123"),
        notificationType = NotificationType.NEWURL,
        message = "Created new url"
      )

      when(mockRepo.getNotificationTypeId(any[String]))
        .thenReturn(Future.successful(Some(1)))

      when(mockRepo.addNotification(any[Notification]))
        .thenReturn(Future.successful(1))

      when(mockRepo.getNotificationStatusId(any[String]))
        .thenReturn(Future.successful(Some(1)))

      val result = router.notifyMethod(request)

      whenReady(result) { reply =>
        reply.success shouldBe true
        reply.message should include("Notification Added successfully")
      }
    }

    "fail if message is empty" in {
      val mockRepo = mock[NotificationRepo]
      val router = new NotificationRouter(mat, system, mockRepo)

      val request = NotificationRequest(
        shortCode = Some("abc123"),
        notificationType = NotificationType.NEWURL,
        message = ""
      )

      val failed = router.notifyMethod(request)

      whenReady(failed.failed) { ex =>
        ex shouldBe a[io.grpc.StatusRuntimeException]
        ex.getMessage should include("INVALID_ARGUMENT")
      }
    }

    "get all notifications" in {
      val mockRepo = mock[NotificationRepo]
      val router = new NotificationRouter(mat, system, mockRepo)

      val notification1 = NotificationDTO(
        1L,
        Some("abc123"),
        None,
        "NEWURL",
        "SUCCESS",
        "Created new url",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      val notification2 = NotificationDTO(
        2L,
        Some("def456"),
        None,
        "TRESHOLD",
        "FAILURE",
        "Threshold reached",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      when(mockRepo.getNotifications)
        .thenReturn(Future.successful(Seq(notification1, notification2)))

      val result = router.getNotifications(Empty())

      whenReady(result) { response =>
        response.notifications.length shouldBe 2
        response.notifications.map(n =>
          (n.shortCode, n.notificationType.toString, n.message)
        ) should contain theSameElementsAs
          Seq(
            (notification1.short_code, notification1.notificationType, notification1.message),
            (notification2.short_code, notification2.notificationType, notification2.message)
          )
      }
    }
  }
}
