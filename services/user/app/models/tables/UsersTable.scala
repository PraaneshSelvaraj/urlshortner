package models.tables

import slick.jdbc.MySQLProfile.api._
import models.User
import slick.lifted.ProvenShape

import java.sql.Timestamp

class UsersTable(tag: Tag) extends Table[User](tag, "users") {
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def username: Rep[String] = column[String]("username", O.Unique)
  def email: Rep[String] = column[String]("email", O.Unique)
  def password: Rep[String] = column[String]("password")
  def is_deleted: Rep[Boolean] = column[Boolean]("is_deleted")
  def created_at: Rep[Timestamp] = column[Timestamp]("created_at")
  def updated_at: Rep[Timestamp] = column[Timestamp]("updated_at")

  override def * : ProvenShape[User] = (
    id,
    username,
    email,
    password,
    is_deleted,
    created_at,
    updated_at
  ) <> ((User.apply _).tupled, User.unapply)
}

object UsersTable {
  val users = TableQuery[UsersTable]
}
