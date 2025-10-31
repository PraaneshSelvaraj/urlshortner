package repositories

import models.Url
import models.tables.UrlsTable.urls
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UrlRepo @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  private val db = dbConfig.db

  def getAllUrls: Future[Seq[Url]] =
    db.run(urls.result)

  def getUrlById(id: Long): Future[Option[Url]] =
    db.run(urls.filter(_.id === id).filter(_.is_deleted === false).result.headOption)

  def getUrlByShortcode(short_code: String): Future[Option[Url]] =
    db.run(
      urls.filter(_.short_code === short_code).filter(_.is_deleted === false).result.headOption
    )

  def addUrl(url: Url): Future[Url] =
    db.run(urls returning urls.map(_.id) += url).flatMap { id =>
      db.run(urls.filter(_.id === id).result.head)
    }

  def deleteUrlByShortCode(shortCode: String): Future[Int] =
    db.run(urls.filter(_.short_code === shortCode).delete)

  def softDeleteUrlsByUserId(userId: Long): Future[Int] =
    db.run(
      urls
        .filter(_.user_id === userId)
        .filter(_.is_deleted === false)
        .map(_.is_deleted)
        .update(true)
    )

  def incrementUrlCount(shortCode: String): Future[Int] = {
    val query = urls.filter(_.short_code === shortCode)
    val action = for {
      currentClicks <- query.map(_.clicks).result.head
      newCount = currentClicks + 1
      _ <- query.map(_.clicks).update(newCount)
    } yield newCount
    db.run(action)
  }

}
