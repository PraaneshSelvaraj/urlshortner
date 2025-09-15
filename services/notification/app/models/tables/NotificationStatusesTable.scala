package models.tables

import slick.jdbc.MySQLProfile.api._
import models.NotificationStatus
import slick.lifted.ProvenShape

class NotificationStatusesTable(tag: Tag)
    extends Table[NotificationStatus](tag, "notification_statuses") {
  def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name: Rep[String] = column[String]("name", O.Unique)

  override def * : ProvenShape[NotificationStatus] =
    (id, name) <> ((NotificationStatus.apply _).tupled, NotificationStatus.unapply)
}

object NotificationStatusesTable {
  val notificationStatuses = TableQuery[NotificationStatusesTable]
}
