package models.tables

import slick.jdbc.MySQLProfile.api._
import models.Notification
import slick.lifted.ProvenShape
import java.sql.Timestamp

class NotificationsTable(tag: Tag) extends Table[Notification](tag, "notifications") {
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def short_code: Rep[Option[String]] = column[Option[String]]("short_code")
  def user_id: Rep[Option[Long]] = column[Option[Long]]("user_id")
  def notification_type_id: Rep[Int] = column[Int]("notification_type_id")
  def notification_status_id: Rep[Int] = column[Int]("notification_status_id")
  def message: Rep[String] = column[String]("message")
  def created_at: Rep[Timestamp] = column[Timestamp]("created_at")
  def updated_at: Rep[Timestamp] = column[Timestamp]("updated_at")

  def notificationTypeFK = foreignKey(
    "fk_notification_type",
    notification_type_id,
    NotificationTypesTable.notificationTypes
  )(_.id)
  override def * : ProvenShape[Notification] = (
    id,
    short_code,
    user_id,
    notification_type_id,
    notification_status_id,
    message,
    created_at,
    updated_at
  ) <> ((Notification.apply _).tupled, Notification.unapply)
}

object NotificationsTable {
  val notifications = TableQuery[NotificationsTable]
}
