package repositories

import models.{Url, User}
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
import org.scalatest.BeforeAndAfterAll

class UrlRepoSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll {

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
  private implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  private val urlRepo = new UrlRepo(dbConfigProvider)
  private val userRepo = new UserRepo(dbConfigProvider)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  var testUserId: Long = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val testUser = User(
      id = 0L,
      username = "testuser",
      email = "test@example.com",
      password = Some("password123"),
      role = "USER",
      google_id = None,
      auth_provider = "LOCAL",
      refresh_token = None,
      is_deleted = false,
      created_at = Timestamp.from(Instant.now()),
      updated_at = Timestamp.from(Instant.now())
    )

    testUserId = whenReady(userRepo.addUser(testUser)) { userID =>
      userID
    }
  }

  "UrlRepo" should {

    "add and get a URL" in {
      val url = Url(
        0L,
        testUserId,
        "abc123",
        "https://example.com",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      val result = for {
        inserted <- urlRepo.addUrl(url)
        found <- urlRepo.getUrlById(inserted.id)
      } yield found

      whenReady(result) { found =>
        found should not be empty
        found.get.short_code shouldBe "abc123"
      }
    }

    "increment clicks" in {
      val url = Url(
        0L,
        testUserId,
        "xyz789",
        "https://test.com",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- urlRepo.addUrl(url)
        count <- urlRepo.incrementUrlCount("xyz789")
        found <- urlRepo.getUrlByShortcode("xyz789")
      } yield (count, found)

      whenReady(result) { case (count, found) =>
        count shouldBe 1
        found.get.clicks shouldBe 1
      }
    }

    "get all Urls" in {
      val url1 = Url(
        0L,
        testUserId,
        "code1",
        "https://scala-lang.org",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      val url2 = Url(
        0L,
        testUserId,
        "code2",
        "https://playframework.com",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      whenReady(urlRepo.addUrl(url1)) { _ =>
        whenReady(urlRepo.addUrl(url2)) { _ =>
          whenReady(urlRepo.getAllUrls()) { urls =>
            urls.map(_.short_code) should contain allOf ("code1", "code2")
          }
        }
      }
    }

    "return Url by Id" in {
      val url = Url(
        0L,
        testUserId,
        "a49g5a",
        "https://scala-lang.org",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      val result = for {
        urlAdded <- urlRepo.addUrl(url)
        urlOpt <- urlRepo.getUrlById(urlAdded.id)
      } yield (urlAdded, urlOpt)

      whenReady(result) { case (urlAdded, urlOpt) =>
        urlOpt shouldBe Some(urlAdded)
      }
    }

    "return none when searching non having id" in {
      whenReady(urlRepo.getUrlById(9999)) { result =>
        result shouldBe None
      }
    }

    "return Url by ShortCode" in {
      val url = Url(
        0L,
        testUserId,
        "eg123",
        "https://example.org",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      val result = for {
        urlAdded <- urlRepo.addUrl(url)
        urlOpt <- urlRepo.getUrlByShortcode(urlAdded.short_code)
      } yield (urlAdded, urlOpt)

      whenReady(result) { case (urlAdded, urlOpt) =>
        urlOpt shouldBe Some(urlAdded)
      }
    }

    "return None when searching for non having code" in {
      whenReady(urlRepo.getUrlByShortcode("testCODE")) { result =>
        result shouldBe None
      }
    }

    "delete url" in {
      val url1 = Url(
        0L,
        testUserId,
        "deleteTest",
        "https://scala-lang.org",
        0,
        false,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      val result = for {
        _ <- urlRepo.addUrl(url1)
        rowsAffected <- urlRepo.deleteUrlByShortCode(url1.short_code)
        url <- urlRepo.getUrlByShortcode(url1.short_code)
      } yield (rowsAffected, url)

      whenReady(result) { case (rowsAffected, urlOpt) =>
        rowsAffected shouldBe 1
        urlOpt shouldBe None
      }
    }
  }
}
