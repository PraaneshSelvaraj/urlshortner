package models.tables

import slick.jdbc.MySQLProfile.api._
import models.Notification
import slick.lifted.ProvenShape
import java.sql.Timestamp

class NotificationsTable(tag: Tag) extends Table[Notification](tag, "notifications"){
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def short_code: Rep[String] = column[String]("short_code")
  def notificationType: Rep[String] = column[String]("notificationType")
  def message: Rep[String] = column[String]("message")
  def created_at: Rep[Timestamp] = column[Timestamp]("created_at", O.Default(new Timestamp(System.currentTimeMillis())))

  override def * : ProvenShape[Notification] = (id, short_code, notificationType, message, created_at) <> ((Notification.apply _).tupled,Notification.unapply)
}

object NotificationsTable {
  val notifications = TableQuery[NotificationsTable]
}