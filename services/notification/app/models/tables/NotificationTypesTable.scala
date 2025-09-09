package models.tables

import slick.jdbc.MySQLProfile.api._
import models.NotificationType
import slick.lifted.ProvenShape

class NotificationTypesTable(tag: Tag) extends Table[NotificationType](tag, "notification_types") {
  def id:Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

  def name:Rep[String] = column[String]("name", O.Unique)

  override def * : ProvenShape[NotificationType] = (id, name) <> ((NotificationType.apply _).tupled, NotificationType.unapply)
}

object NotificationTypesTable {
  val notificationTypes = TableQuery[NotificationTypesTable]
}