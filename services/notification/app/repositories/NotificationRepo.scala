package repositories

import models.Notification
import models.tables.NotificationsTable.notifications
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class NotificationRepo @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  def addNotification(notification: Notification):Future[Int] = dbConfig.db.run(
    notifications += notification
  )

  def getNotifications: Future[Seq[Notification]] = dbConfig.db.run(
    notifications.result
  )

}
