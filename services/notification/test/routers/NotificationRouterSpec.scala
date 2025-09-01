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
        shortCode = "abc123",
        notificationType = NotificationType.NEWURL,
        message = "Created new url"
      )

      when(mockRepo.addNotification(any[Notification]))
        .thenReturn(Future.successful(1))

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
        shortCode = "abc123",
        notificationType = NotificationType.NEWURL,
        message = ""
      )

      val failed = router.notifyMethod(request)

      whenReady(failed.failed) { ex =>
        ex shouldBe a[io.grpc.StatusRuntimeException]
        ex.getMessage should include("INVALID_ARGUMENT")
      }
    }

    "fail if shortCode is empty" in {
      val mockRepo = mock[NotificationRepo]
      val router = new NotificationRouter(mat, system, mockRepo)

      val request = NotificationRequest(
        shortCode = "",
        notificationType = NotificationType.NEWURL,
        message = "Created new url"
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

      val notification1 = Notification(1L, "abc123", "NEWURL", "Created new url", Timestamp.from(Instant.now()))
      val notification2 = Notification(2L, "abc123", "TRESHOLD", "Threshold reached", Timestamp.from(Instant.now()))

      when(mockRepo.getNotifications)
        .thenReturn(Future.successful(Seq(notification1, notification2)))

      val result = router.getNotifications(Empty())

      whenReady(result) { response =>
        response.notifications.map(_.shortCode) should contain("abc123")
        response.notifications.map(_.notificationType) should contain allOf(NotificationType.NEWURL, NotificationType.TRESHOLD)
      }
    }
  }
}
