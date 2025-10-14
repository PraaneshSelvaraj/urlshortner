package repositories

import models.User
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
import org.mindrot.jbcrypt.BCrypt

class UserRepoSpec
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
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:usertest;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        "play.evolutions.enabled" -> "true",
        "play.evolutions.autoApply" -> "true"
      )
      .build()

  private val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  private implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  private val userRepo = new UserRepo(dbConfigProvider)

  private val testPassword = "SecurePassword123"
  private val hashedPassword = BCrypt.hashpw(testPassword, BCrypt.gensalt())

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "UserRepo" should {

    "add and get a User" in {
      val user = User(
        id = 0L,
        username = "user",
        email = "user@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userFetched <- userRepo.findUserByEmail(user.email)
      } yield (userId, userFetched)

      whenReady(result) { case (userId, userFetched) =>
        userFetched should not be empty
        userFetched.get.id shouldBe userId
        userFetched.get.email shouldBe user.email
        userFetched.get.username shouldBe user.username
      }
    }

    "get all Users" in {
      val user1 = User(
        id = 0L,
        username = "user1",
        email = "user1@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val user2 = User(
        id = 0L,
        username = "user2",
        email = "user2@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user1)
        _ <- userRepo.addUser(user2)
        users <- userRepo.getAllUsers
      } yield users

      whenReady(result) { users =>
        users.map(_.username) should contain allOf ("user1", "user2")
      }
    }

    "find user by Email" in {
      val user = User(
        id = 0L,
        username = "user3",
        email = "user3@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userFetched <- userRepo.findUserByEmail(user.email)
      } yield (userId, userFetched)

      whenReady(result) { case (userId, userFetched) =>
        userFetched should not be empty
        userFetched.get.id shouldBe userId
        userFetched.get.email shouldBe user.email
      }
    }

    "return none when searching invalid email" in {
      whenReady(userRepo.findUserByEmail("invalid@email.com")) { result =>
        result shouldBe None
      }
    }

    "not allow duplicate email" in {
      val user = User(
        id = 0L,
        username = "user4",
        email = "user4@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user)
        duplicate <- userRepo.addUser(user.copy(username = "tempuser6")).failed
      } yield duplicate

      whenReady(result) { exception =>
        exception shouldBe a[java.sql.SQLException]
      }
    }

    "not allow duplicate username" in {
      val user = User(
        id = 0L,
        username = "user5",
        email = "user5@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user)
        duplicate <- userRepo.addUser(user.copy(email = "tempuser7@example.com")).failed
      } yield duplicate

      whenReady(result) { exception =>
        exception shouldBe a[java.sql.SQLException]
      }
    }

    "authenticate upon valid credentials" in {
      val user = User(
        id = 0L,
        username = "user6",
        email = "user6@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userAuthenticated <- userRepo.authenticate(user.email, testPassword)
      } yield (userId, userAuthenticated)

      whenReady(result) { case (userId, userAuthenticated) =>
        userAuthenticated should not be empty
        userAuthenticated.get.id shouldBe userId
        userAuthenticated.get.email shouldBe user.email
        userAuthenticated.get.username shouldBe user.username
      }
    }

    "fail to authenticate upon invalid credentials" in {
      val user = User(
        id = 0L,
        username = "user7",
        email = "user7@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user)
        userAuthenticated <- userRepo.authenticate(user.email, "INVALID_PASSWORD")
      } yield userAuthenticated

      whenReady(result) { userAuthenticated =>
        userAuthenticated shouldBe None
      }
    }

    "fail to authenticate with non-existent email" in {
      val result = userRepo.authenticate("nonexistent@example.com", testPassword)

      whenReady(result) { userAuthenticated =>
        userAuthenticated shouldBe None
      }
    }

    "fail to authenticate with empty password" in {
      val user = User(
        id = 0L,
        username = "user8",
        email = "user8@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user)
        userAuthenticated <- userRepo.authenticate(user.email, "")
      } yield userAuthenticated

      whenReady(result) { userAuthenticated =>
        userAuthenticated shouldBe None
      }
    }

  }
}
