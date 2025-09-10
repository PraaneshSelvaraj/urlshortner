package repositories

import models.Url
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import scala.concurrent.ExecutionContext
import java.sql.Timestamp
import java.time.Instant
import org.scalatest.time.{Millis, Seconds, Span}

class UrlRepoSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:play;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        "play.evolutions.enabled" -> "true",
        "play.evolutions.autoApply" -> "true"
      )
      .build()

  private val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  private implicit val ec: ExecutionContext =
    app.injector.instanceOf[ExecutionContext]
  private val repo = new UrlRepo(dbConfigProvider)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "UrlRepo" should {

    "add and get a URL" in {
      val url = Url(0L, "abc123", "https://example.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      val result = for {
        inserted <- repo.addUrl(url)
        found <- repo.getUrlById(inserted.id)
      } yield found

      whenReady(result) { found =>
        found should not be empty
        found.get.short_code shouldBe "abc123"
      }
    }

    "increment clicks" in {
      val url = Url(0L, "xyz789", "https://test.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      val result = for {
        _ <- repo.addUrl(url)
        count <- repo.incrementUrlCount("xyz789")
        found <- repo.getUrlByShortcode("xyz789")
      } yield (count, found)

      whenReady(result) { case (count, found) =>
        count shouldBe 1
        found.get.clicks shouldBe 1
      }
    }

    "get all Urls" in {
      val url1 = Url(0L, "code1", "https://scala-lang.org", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
      val url2 = Url(0L, "code2", "https://playframework.com", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      whenReady(repo.addUrl(url1)) { _ =>
        whenReady(repo.addUrl(url2)) { _ =>
          whenReady(repo.getAllUrls) { urls =>
            urls.map(_.short_code) should contain allOf("code1", "code2")
          }
        }
      }
    }

    "return none when searching non having id" in {
      whenReady(repo.getUrlById(9999)) { result =>
        result shouldBe None
      }
    }

    "return None when searching for non having code" in {
      whenReady(repo.getUrlByShortcode("testCODE")) { result =>
        result shouldBe None
      }
    }

    "delete url" in {
      val url1 = Url(0L, "deleteTest", "https://scala-lang.org", 0, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
      val result = for {
        _ <- repo.addUrl(url1)
        rowsAffected <- repo.deleteUrlByShortCode(url1.short_code)
        url <- repo.getUrlByShortcode(url1.short_code)
      } yield (rowsAffected, url)

      whenReady(result) {
        case (rowsAffected, urlOpt) =>
          rowsAffected shouldBe 1
          urlOpt shouldBe None
      }
    }
  }
}
