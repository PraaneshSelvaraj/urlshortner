package routers

import com.google.protobuf.empty.Empty
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.GrpcServiceException
import org.apache.pekko.stream.Materializer
import example.urlshortner.notification.grpc
import example.urlshortner.notification.grpc._
import io.grpc.Status
import repositories.NotificationRepo
import models.Notification

import javax.inject.Inject
import scala.concurrent.{ExecutionContextExecutor, Future}
import java.sql.Timestamp
import java.time.Instant

class NotificationRouter @Inject() (
    mat: Materializer,
    system: ActorSystem,
    val notificationRepo: NotificationRepo
) extends AbstractNotificationServiceRouter(system) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def notifyMethod(in: NotificationRequest): Future[NotificationReply] = {

    if (in.message.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("message is required")
        )
      )
    } else {
      val notificationTypeName = in.notificationType.toString()

      for {
        typeId <- notificationRepo.getNotificationTypeId(notificationTypeName) flatMap {
          case Some(id) => Future.successful(id)
          case None =>
            Future.failed(
              new GrpcServiceException(
                status = Status.INVALID_ARGUMENT.withDescription(
                  s"Invalid notification type: $notificationTypeName"
                )
              )
            )
        }
        statusId <- notificationRepo.getNotificationStatusId("SUCCESS") flatMap {
          case Some(id) => Future.successful(id)
          case None =>
            Future.failed(
              new GrpcServiceException(
                status = Status.INVALID_ARGUMENT.withDescription(
                  s"Invalid notification type: $notificationTypeName"
                )
              )
            )
        }
        newNotification = Notification(
          id = 0L,
          short_code = in.shortCode,
          user_id = in.userId,
          notification_type_id = typeId,
          notification_status_id = statusId,
          message = in.message,
          created_at = Timestamp.from(Instant.now()),
          updated_at = Timestamp.from(Instant.now())
        )
        rowsAffected <- notificationRepo.addNotification(newNotification)
      } yield {
        if (rowsAffected <= 0) {
          throw new GrpcServiceException(
            status = Status.INTERNAL.withDescription("Unable to add Notification due to db issue")
          )
        } else {
          val shortCode = newNotification.short_code match {
            case Some(code) => code
            case None       => "None"
          }
          val userId = newNotification.user_id match {
            case Some(id) => id.toString()
            case None     => "None"
          }

          println(
            s"NOTIFICATION ($notificationTypeName) - status: SUCCESS - shortCode: $shortCode - userId: $userId - message: ${newNotification.message}"
          )
          NotificationReply(
            success = true,
            message = "Notification Added successfully",
            notificationStatus = NotificationStatus.SUCCESS
          )
        }
      }

    }
  }

  override def getNotifications(in: Empty): Future[GetNotificationsResponse] =
    notificationRepo.getNotifications map { notifications =>
      val grpcNotifications = notifications.map { notification =>
        val notificationType = notification.notificationType match {
          case "NEWURL"   => NotificationType.NEWURL
          case "TRESHOLD" => NotificationType.TRESHOLD
          case "NEWUSER"  => NotificationType.NEWUSER
          case _ =>
            throw new NoSuchElementException(
              s"Unknown notification type: ${notification.notificationType}"
            )
        }

        val notificationStatus = notification.notificationStatus match {
          case "SUCCESS" => NotificationStatus.SUCCESS
          case "FAILURE" => NotificationStatus.FAILURE
          case "PENDING" => NotificationStatus.PENDING
          case _ =>
            throw new NoSuchElementException(
              s"Unknown notification status: ${notification.notificationStatus}"
            )
        }

        grpc.Notification(
          id = notification.id,
          shortCode = notification.short_code,
          notificationType = notificationType,
          notificationStatus = notificationStatus,
          message = notification.message
        )
      }
      GetNotificationsResponse(notifications = grpcNotifications)
    }
}
