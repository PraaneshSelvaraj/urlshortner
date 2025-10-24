package auth

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import play.api.test.Helpers._
import play.api.test._
import repositories.UserRepo
import security.JwtUtility
import models.User
import pdi.jwt.{JwtClaim, JwtAlgorithm}
import pdi.jwt.exceptions.{JwtExpirationException, JwtValidationException}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import java.sql.Timestamp
import java.time.Instant
import org.scalatest.BeforeAndAfterEach

class AuthenticatedActionSpec
    extends PlaySpec
    with MockitoSugar
    with DefaultAwaitTimeout
    with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  val stubControllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  val mockJwtUtility: JwtUtility = mock[JwtUtility]
  val mockUserRepo: UserRepo = mock[UserRepo]

  val defaultBodyParser = new BodyParsers.Default(stubControllerComponents.parsers)

  val authenticatedAction = new AuthenticatedAction(
    defaultBodyParser,
    mockJwtUtility,
    mockUserRepo
  )

  val sampleUser = User(
    id = 1L,
    username = "testuser",
    email = "test@example.com",
    password = Some("hashedpassword"),
    role = "USER",
    google_id = None,
    auth_provider = "local",
    refresh_token = None,
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  val sampleAdmin = User(
    id = 2L,
    username = "adminuser",
    email = "admin@example.com",
    password = Some("hashedpassword"),
    role = "ADMIN",
    google_id = None,
    auth_provider = "local",
    refresh_token = None,
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  val validClaims = JwtClaim(
    content = """{"email":"test@example.com","role":"USER"}"""
  )

  val adminClaims = JwtClaim(
    content = """{"email":"admin@example.com","role":"ADMIN"}"""
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJwtUtility, mockUserRepo)
  }

  "AuthenticatedAction#forUser" should {

    "allow access for valid USER token" in {
      when(mockJwtUtility.decodeToken("valid_user_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(Some(sampleUser)))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_user_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "userId").as[Long] mustBe 1L
      verify(mockJwtUtility).decodeToken("valid_user_token")
      verify(mockUserRepo).findUserByEmail("test@example.com")
    }

    "deny access for ADMIN trying to access USER-only endpoint" in {
      when(mockJwtUtility.decodeToken("valid_admin_token"))
        .thenReturn(Success(adminClaims))
      when(mockJwtUtility.getClaimsData(adminClaims))
        .thenReturn(Some(("admin@example.com", "ADMIN")))
      when(mockUserRepo.findUserByEmail("admin@example.com"))
        .thenReturn(Future.successful(Some(sampleAdmin)))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_admin_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] must include("Access denied")
      (contentAsJson(result) \ "message").as[String] must include("ADMIN")
    }
  }

  "AuthenticatedAction#forAdmin" should {

    "allow access for valid ADMIN token" in {
      when(mockJwtUtility.decodeToken("valid_admin_token"))
        .thenReturn(Success(adminClaims))
      when(mockJwtUtility.getClaimsData(adminClaims))
        .thenReturn(Some(("admin@example.com", "ADMIN")))
      when(mockUserRepo.findUserByEmail("admin@example.com"))
        .thenReturn(Future.successful(Some(sampleAdmin)))

      val request = FakeRequest(GET, "/admin/test")
        .withHeaders("Authorization" -> "Bearer valid_admin_token")

      val action = authenticatedAction.forAdmin { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "userId").as[Long] mustBe 2L
    }

    "deny access for USER trying to access ADMIN-only endpoint" in {
      when(mockJwtUtility.decodeToken("valid_user_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(Some(sampleUser)))

      val request = FakeRequest(GET, "/admin/test")
        .withHeaders("Authorization" -> "Bearer valid_user_token")

      val action = authenticatedAction.forAdmin { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] must include("Access denied")
      (contentAsJson(result) \ "message").as[String] must include("USER")
    }
  }

  "AuthenticatedAction#forUserOrAdmin" should {

    "allow access for USER token" in {
      when(mockJwtUtility.decodeToken("valid_user_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(Some(sampleUser)))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_user_token")

      val action = authenticatedAction.forUserOrAdmin { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "userId").as[Long] mustBe 1L
    }

    "allow access for ADMIN token" in {
      when(mockJwtUtility.decodeToken("valid_admin_token"))
        .thenReturn(Success(adminClaims))
      when(mockJwtUtility.getClaimsData(adminClaims))
        .thenReturn(Some(("admin@example.com", "ADMIN")))
      when(mockUserRepo.findUserByEmail("admin@example.com"))
        .thenReturn(Future.successful(Some(sampleAdmin)))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_admin_token")

      val action = authenticatedAction.forUserOrAdmin { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("userId" -> authReq.user.id))
      }

      val result = action(request)

      status(result) mustBe OK
      (contentAsJson(result) \ "userId").as[Long] mustBe 2L
    }
  }

  "AuthenticatedAction" should {

    "return 401 when Authorization header is missing" in {
      val request = FakeRequest(GET, "/test")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Authorization header missing")
    }

    "return 401 when Authorization header is malformed (no Bearer prefix)" in {
      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "InvalidToken")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Authorization header malformed")
      (contentAsJson(result) \ "message").as[String] must include("Bearer <token>")
    }

    "return 401 when token is expired" in {
      val exception = new JwtExpirationException(0L)
      when(mockJwtUtility.decodeToken("expired_token"))
        .thenReturn(Failure(exception))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer expired_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] mustBe "Token expired"
    }

    "return 401 when token signature is invalid" in {
      val exception = new JwtValidationException("Invalid signature")
      when(mockJwtUtility.decodeToken("invalid_signature_token"))
        .thenReturn(Failure(exception))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer invalid_signature_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Invalid token signature")
    }

    "return 401 when token decoding fails with generic exception" in {
      val exception = new Exception("Generic token error")
      when(mockJwtUtility.decodeToken("bad_token"))
        .thenReturn(Failure(exception))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer bad_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Invalid token")
      (contentAsJson(result) \ "message").as[String] must include("Generic token error")
    }

    "return 401 when token claims are missing email or role" in {
      when(mockJwtUtility.decodeToken("valid_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(None)

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Invalid token")
      (contentAsJson(result) \ "message").as[String] must include("Missing required claims")
    }

    "return 401 when user is not found in database" in {
      when(mockJwtUtility.decodeToken("valid_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(None))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("Invalid token")
      (contentAsJson(result) \ "message").as[String] must include("User not found")
    }

    "return 401 when user is deactivated" in {
      val deactivatedUser = sampleUser.copy(is_deleted = true)

      when(mockJwtUtility.decodeToken("valid_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(None)) // Repository should return None for deleted users

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val action = authenticatedAction.forUser { authReq: AuthenticatedRequest[AnyContent] =>
        Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] must include("deactivated")
    }

    "return 403 when USER tries to access endpoint requiring different role" in {
      when(mockJwtUtility.decodeToken("valid_token"))
        .thenReturn(Success(validClaims))
      when(mockJwtUtility.getClaimsData(validClaims))
        .thenReturn(Some(("test@example.com", "USER")))
      when(mockUserRepo.findUserByEmail("test@example.com"))
        .thenReturn(Future.successful(Some(sampleUser)))

      val request = FakeRequest(GET, "/test")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val action = authenticatedAction.apply(Set("ADMIN")) {
        authReq: AuthenticatedRequest[AnyContent] =>
          Ok(Json.obj("success" -> true))
      }

      val result = action(request)

      status(result) mustBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] must include("Access denied")
      (contentAsJson(result) \ "message").as[String] must include("USER")
    }
  }
}
