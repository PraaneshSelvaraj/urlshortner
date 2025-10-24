package repositories

import models.User
import models.tables.UsersTable
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import slick.jdbc.H2Profile.api._
import org.mindrot.jbcrypt.BCrypt

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

class UserRepoSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:usertest;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        "play.evolutions.enabled" -> "false"
      )
      .build()

  private val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  private implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  private val userRepo = new UserRepo(dbConfigProvider)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  private val db = dbConfigProvider.get.db

  private val testPassword = "SecurePassword123"
  private val hashedPassword = BCrypt.hashpw(testPassword, BCrypt.gensalt())

  override def beforeAll(): Unit = {
    super.beforeAll()
    val setup = UsersTable.users.schema.create
    db.run(setup).futureValue
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    val clearDb = UsersTable.users.delete
    db.run(clearDb).futureValue
  }

  "UserRepo" should {

    "add a user and retrieve by ID" in {
      val user = User(
        id = 0L,
        username = "testuser",
        email = "test@example.com",
        password = Some("plainPassword123"),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userFetched <- userRepo.getUserById(userId)
      } yield (userId, userFetched)

      whenReady(result) { case (userId, userFetched) =>
        userFetched should not be empty
        userFetched.get.id shouldBe userId
        userFetched.get.username shouldBe "testuser"
        userFetched.get.email shouldBe "test@example.com"
        userFetched.get.password.get should startWith("$2")
      }
    }

    "get all users" in {
      val user1 = User(
        id = 0L,
        username = "user1",
        email = "user1@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
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
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val user3 = User(
        id = 0L,
        username = "user3",
        email = "user3@example.com",
        password = Some(hashedPassword),
        role = "ADMIN",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user1)
        _ <- userRepo.addUser(user2)
        _ <- userRepo.addUser(user3)
        users <- userRepo.getAllUsers
      } yield users

      whenReady(result) { users =>
        users.length shouldBe 3
        users.map(_.username) should contain allOf ("user1", "user2", "user3")
        users.map(
          _.email
        ) should contain allOf ("user1@example.com", "user2@example.com", "user3@example.com")
      }
    }

    "find user by email" in {
      val user = User(
        id = 0L,
        username = "emailuser",
        email = "emailuser@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userFetched <- userRepo.findUserByEmail("emailuser@example.com")
      } yield (userId, userFetched)

      whenReady(result) { case (userId, userFetched) =>
        userFetched should not be empty
        userFetched.get.id shouldBe userId
        userFetched.get.email shouldBe "emailuser@example.com"
        userFetched.get.username shouldBe "emailuser"
      }
    }

    "return None when searching for invalid email" in {
      whenReady(userRepo.findUserByEmail("nonexistent@example.com")) { result =>
        result shouldBe None
      }
    }

    "return None when getting user by non-existent ID" in {
      whenReady(userRepo.getUserById(9999L)) { result =>
        result shouldBe None
      }
    }

    "authenticate user with valid credentials" in {
      val plainPassword = "MySecurePassword123"
      val user = User(
        id = 0L,
        username = "authuser",
        email = "authuser@example.com",
        password = Some(plainPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        authenticated <- userRepo.authenticate("authuser@example.com", plainPassword)
      } yield (userId, authenticated)

      whenReady(result) { case (userId, authenticated) =>
        authenticated should not be empty
        authenticated.get.id shouldBe userId
        authenticated.get.email shouldBe "authuser@example.com"
        authenticated.get.username shouldBe "authuser"
      }
    }

    "fail to authenticate with invalid password" in {
      val user = User(
        id = 0L,
        username = "authuser2",
        email = "authuser2@example.com",
        password = Some("CorrectPassword123"),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(user)
        authenticated <- userRepo.authenticate("authuser2@example.com", "WrongPassword123")
      } yield authenticated

      whenReady(result) { authenticated =>
        authenticated shouldBe None
      }
    }

    "fail to authenticate with non-existent email" in {
      whenReady(userRepo.authenticate("nonexistent@example.com", "anyPassword")) { result =>
        result shouldBe None
      }
    }

    "fail to authenticate Google OAuth user with password" in {
      val googleUser = User(
        id = 0L,
        username = "googleuser",
        email = "google@example.com",
        password = None,
        role = "USER",
        google_id = Some("google_12345"),
        auth_provider = "GOOGLE",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- userRepo.addUser(googleUser)
        authenticated <- userRepo.authenticate("google@example.com", "anyPassword")
      } yield authenticated

      whenReady(result) { authenticated =>
        authenticated shouldBe None
      }
    }

    "soft delete user by ID" in {
      val user = User(
        id = 0L,
        username = "deleteuser",
        email = "delete@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        rowsAffected <- userRepo.deleteUserById(userId)
        deletedUser <- userRepo.getUserById(userId)
      } yield (rowsAffected, deletedUser)

      whenReady(result) { case (rowsAffected, deletedUser) =>
        rowsAffected shouldBe 1
        deletedUser should not be empty
        deletedUser.get.is_deleted shouldBe true
      }
    }

    "return 0 when deleting non-existent user" in {
      whenReady(userRepo.deleteUserById(9999L)) { rowsAffected =>
        rowsAffected shouldBe 0
      }
    }

    "return 0 when deleting already deleted user" in {
      val user = User(
        id = 0L,
        username = "deleteduser",
        email = "deleted@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        firstDelete <- userRepo.deleteUserById(userId)
        secondDelete <- userRepo.deleteUserById(userId)
      } yield (firstDelete, secondDelete)

      whenReady(result) { case (firstDelete, secondDelete) =>
        firstDelete shouldBe 1
        secondDelete shouldBe 0
      }
    }

    "not allow duplicate email" in {
      val user1 = User(
        id = 0L,
        username = "user1",
        email = "duplicate@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val user2 = user1.copy(username = "user2")

      val result = for {
        _ <- userRepo.addUser(user1)
        duplicate <- userRepo.addUser(user2).failed
      } yield duplicate

      whenReady(result) { exception =>
        exception shouldBe a[java.sql.SQLException]
      }
    }

    "not allow duplicate username" in {
      val user1 = User(
        id = 0L,
        username = "uniqueuser",
        email = "email1@example.com",
        password = Some(hashedPassword),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val user2 = user1.copy(email = "email2@example.com")

      val result = for {
        _ <- userRepo.addUser(user1)
        duplicate <- userRepo.addUser(user2).failed
      } yield duplicate

      whenReady(result) { exception =>
        exception shouldBe a[java.sql.SQLException]
      }
    }

    "handle Google OAuth user without password" in {
      val googleUser = User(
        id = 0L,
        username = "googleoauthuser",
        email = "googleoauth@example.com",
        password = None,
        role = "USER",
        google_id = Some("google_67890"),
        auth_provider = "GOOGLE",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(googleUser)
        userFetched <- userRepo.getUserById(userId)
      } yield userFetched

      whenReady(result) { userFetched =>
        userFetched should not be empty
        userFetched.get.password shouldBe None
        userFetched.get.google_id shouldBe Some("google_67890")
        userFetched.get.auth_provider shouldBe "GOOGLE"
      }
    }

    "not hash already hashed passwords" in {
      val alreadyHashed = BCrypt.hashpw("password", BCrypt.gensalt())
      val user = User(
        id = 0L,
        username = "hasheduser",
        email = "hashed@example.com",
        password = Some(alreadyHashed),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      val result = for {
        userId <- userRepo.addUser(user)
        userFetched <- userRepo.getUserById(userId)
      } yield userFetched

      whenReady(result) { userFetched =>
        userFetched should not be empty
        userFetched.get.password shouldBe Some(alreadyHashed)
      }
    }
  }
}
