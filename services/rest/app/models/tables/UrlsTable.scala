package models.tables

import slick.jdbc.MySQLProfile.api._
import models.Url
import slick.lifted.ProvenShape

import java.sql.Timestamp

class UrlsTable(tag: Tag) extends Table[Url](tag, "urls") {
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def short_code: Rep[String] = column[String]("short_code", O.Unique)
  def long_url: Rep[String] = column[String]("long_url")
  def clicks: Rep[Int] = column[Int]("clicks", O.Default(0))
  def created_at: Rep[Timestamp] = column[Timestamp]("created_at")
  def updated_at: Rep[Timestamp] = column[Timestamp]("updated_at")
  def expires_at: Rep[Timestamp] = column[Timestamp]("expires_at")

  override def * : ProvenShape[Url] = (
    id,
    short_code,
    long_url,
    clicks,
    created_at,
    updated_at,
    expires_at
  ) <> ((Url.apply _).tupled, Url.unapply)
}

object UrlsTable {
  val urls = TableQuery[UrlsTable]
}
