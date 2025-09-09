package repositories

import models.{Notification, NotificationType, NotificationWithType}
import models.tables.NotificationsTable.notifications
import models.tables.NotificationTypesTable.notificationTypes
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class NotificationRepo @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  def getNotificationTypeId(name: String): Future[Option[Int]] = dbConfig.db.run(
    notificationTypes.filter(_.name === name).map(_.id).result.headOption
  )

  def addNotification(notification: Notification):Future[Int] = dbConfig.db.run(
    notifications += notification
  )

  def getNotificationsWithTypeName: Future[Seq[NotificationWithType]] = dbConfig.db.run(
    (for {
      n <- notifications
      t <- notificationTypes if n.notification_type_id === t.id
    } yield (n.id, n.short_code, t.name, n.message, n.created_at, n.updated_at))
      .result
      .map(_.map {
        case (id, short_code, typeName, message, created_at, updated_at) =>
          NotificationWithType(id, short_code, typeName, message, created_at, updated_at)
      })
  )

}
