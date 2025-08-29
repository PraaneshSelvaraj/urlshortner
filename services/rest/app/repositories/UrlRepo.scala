package repositories

import models.Url
import models.tables.UrlsTable.urls
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UrlRepo @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  def getAllUrls: Future[Seq[Url]] = dbConfig.db.run(urls.result)

  def getUrlById(id: Long): Future[Option[Url]] = dbConfig.db.run(urls.filter(_.id === id).result.headOption)

  def getUrlByShortcode(short_code: String): Future[Option[Url]] = dbConfig.db.run(urls.filter(_.short_code === short_code).result.headOption)

  def addUrl(url: Url): Try[Future[Url]] = Try {
    dbConfig.db.run(urls returning urls.map(_.id) += url) flatMap {
      id => dbConfig.db.run(urls.filter(_.id === id).result.head)
    }
  }


  def incrementUrlCount(shortCode: String): Future[Int] = dbConfig.db.run {
    val query = urls.filter(_.short_code === shortCode)
    query.map(_.clicks).result.head.flatMap { currentClicks =>
      val newCount = currentClicks + 1
      query.map(_.clicks).update(newCount).map(_ => newCount)
    }
  }

}