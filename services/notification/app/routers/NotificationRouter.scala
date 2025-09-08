package routers

import com.google.protobuf.empty.Empty
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.GrpcServiceException
import org.apache.pekko.stream.Materializer
import example.urlshortner.notification.grpc
import example.urlshortner.notification.grpc.{NotificationRequest, NotificationReply, AbstractNotificationServiceRouter, GetNotificationsResponse, NotificationType}
import io.grpc.Status
import repositories.NotificationRepo
import models.Notification
import javax.inject.Inject
import scala.concurrent.{ExecutionContextExecutor, Future}
import java.sql.Timestamp

class NotificationRouter @Inject()(mat: Materializer, system: ActorSystem, val notificationRepo: NotificationRepo) extends AbstractNotificationServiceRouter(system) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def notifyMethod(in: NotificationRequest): Future[NotificationReply] = {

    if(in.message.isEmpty){
      Future.failed(new GrpcServiceException(
        status = Status.INVALID_ARGUMENT.withDescription("message is required")
      ))
    }
    else if(in.shortCode.isEmpty){
      Future.failed(new GrpcServiceException(
        status = Status.INVALID_ARGUMENT.withDescription("shortCode is required")
      ))
    }
    else {
      val newNotification = Notification(
        id = 0L,
        short_code = in.shortCode,
        notificationType = in.notificationType.toString(),
        message = in.message,
        created_at = new Timestamp(System.currentTimeMillis()),
        updated_at = new Timestamp(System.currentTimeMillis())
      )
      notificationRepo.addNotification(newNotification).flatMap{
        rowsAffected => {
          if(rowsAffected <=0) Future.failed(new GrpcServiceException(
            status = Status.INTERNAL.withDescription("Unable to add Notification due to db issue")
          ))
          else {
            println(s"NOTIFICATION (${newNotification.notificationType}) - shortCode: ${newNotification.short_code} - message: ${newNotification.message}")
            Future.successful(NotificationReply(success = true, "Notification Added successfully"))
          }
        }
      }
    }
  }

  override def getNotifications(in: Empty): Future[GetNotificationsResponse] = notificationRepo.getNotifications.map { notifications =>
    val grpcNotifications = notifications.map { notification =>
      grpc.Notification(
        id = notification.id,
        shortCode = notification.short_code,
        notificationType = notification.notificationType match {
          case "NEWURL" => NotificationType.NEWURL
          case "TRESHOLD" => NotificationType.TRESHOLD
        },
        message = notification.message
      )
    }
    GetNotificationsResponse(notifications = grpcNotifications)
  }
}
