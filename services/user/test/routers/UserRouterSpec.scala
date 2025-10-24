package routers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.grpc.GrpcServiceException
import example.urlshortner.user.grpc._
import io.grpc.Status
import models.User
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.{ExecutionContext, Future}
import java.sql.{SQLException, Timestamp}
import java.time.Instant
import repositories.UserRepo
import security.JwtUtility
import services.{GoogleAuthService, GoogleUserInfo}
import org.mindrot.jbcrypt.BCrypt

class UserRouterSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  private val mockUserRepo = mock[UserRepo]
  private val mockJwtUtility = mock[JwtUtility]
  private val mockGoogleAuthService = mock[GoogleAuthService]

  private val router = new UserRouter(
    mat,
    system,
    mockUserRepo,
    mockJwtUtility,
    mockGoogleAuthService
  )

  private val hashedPassword = BCrypt.hashpw("password123", BCrypt.gensalt())

  private val sampleUser = User(
    id = 1L,
    username = "testuser",
    email = "test@example.com",
    password = Some(hashedPassword),
    role = "USER",
    google_id = None,
    auth_provider = "LOCAL",
    refresh_token = None,
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserRepo, mockJwtUtility, mockGoogleAuthService)
  }

  "UserRouter#createUser" should {

    "create a new user successfully" in {
      val request = CreateUserRequest(
        username = "newuser",
        email = "newuser@example.com",
        password = "password123"
      )

      when(mockUserRepo.addUser(any[User]))
        .thenReturn(Future.successful(1L))

      val result = router.createUser(request)

      whenReady(result) { user =>
        user.id shouldBe 1L
        user.username shouldBe "newuser"
        user.email shouldBe "newuser@example.com"
        user.authProvider shouldBe AuthProvider.LOCAL
        user.isDeleted shouldBe false
        verify(mockUserRepo).addUser(any[User])
      }
    }

    "fail when username is empty" in {
      val request = CreateUserRequest(
        username = "",
        email = "test@example.com",
        password = "password123"
      )

      val result = router.createUser(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("Username is required")
      }
    }

    "fail when email is empty" in {
      val request = CreateUserRequest(
        username = "testuser",
        email = "",
        password = "password123"
      )

      val result = router.createUser(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("Email is required")
      }
    }

    "fail when password is empty" in {
      val request = CreateUserRequest(
        username = "testuser",
        email = "test@example.com",
        password = ""
      )

      val result = router.createUser(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("Password is required")
      }
    }

    "fail when user already exists (duplicate email/username)" in {
      val request = CreateUserRequest(
        username = "existinguser",
        email = "existing@example.com",
        password = "password123"
      )

      val sqlException = new SQLException("Duplicate entry", "23000", 1062)
      when(mockUserRepo.addUser(any[User]))
        .thenReturn(Future.failed(sqlException))

      val result = router.createUser(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.ALREADY_EXISTS
      }
    }

    "fail with internal error on database failure" in {
      val request = CreateUserRequest(
        username = "testuser",
        email = "test@example.com",
        password = "password123"
      )

      when(mockUserRepo.addUser(any[User]))
        .thenReturn(Future.failed(new SQLException("Database connection failed")))

      val result = router.createUser(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception.asInstanceOf[GrpcServiceException].status.getCode shouldBe Status.Code.INTERNAL
      }
    }
  }

  "UserRouter#userLogin" should {

    "login successfully with valid credentials" in {
      val request = LoginRequest(
        email = "test@example.com",
        password = "password123"
      )

      when(mockUserRepo.authenticate(request.email, request.password))
        .thenReturn(Future.successful(Some(sampleUser)))

      when(mockUserRepo.updateRefreshToken(any[Long](), any[String]()))
        .thenReturn(Future.successful(1))

      when(mockJwtUtility.createToken(sampleUser.email, sampleUser.role))
        .thenReturn("access_token_123")

      when(mockJwtUtility.createRefreshToken(sampleUser.email, sampleUser.role))
        .thenReturn("refresh_token_123")

      val result = router.userLogin(request)

      whenReady(result) { response =>
        response.success shouldBe true
        response.isUserCreated shouldBe false
        response.accessToken shouldBe "access_token_123"
        response.refreshToken shouldBe "refresh_token_123"
        response.message should include("Login was Successfull")
        response.user should not be empty
        response.user.get.email shouldBe sampleUser.email
        verify(mockUserRepo).authenticate(request.email, request.password)
        verify(mockJwtUtility).createToken(sampleUser.email, sampleUser.role)
      }
    }

    "fail when email is empty" in {
      val request = LoginRequest(
        email = "",
        password = "password123"
      )

      val result = router.userLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("Email is required")
      }
    }

    "fail when password is empty" in {
      val request = LoginRequest(
        email = "test@example.com",
        password = ""
      )

      val result = router.userLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("Password is required")
      }
    }

    "fail with invalid credentials" in {
      val request = LoginRequest(
        email = "test@example.com",
        password = "wrongpassword"
      )

      when(mockUserRepo.authenticate(request.email, request.password))
        .thenReturn(Future.successful(None))

      val result = router.userLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.PERMISSION_DENIED
        exception.getMessage should include("Invalid Credentials")
      }
    }

    "fail when account is deactivated" in {
      val deletedUser = sampleUser.copy(is_deleted = true)
      val request = LoginRequest(
        email = "test@example.com",
        password = "password123"
      )

      when(mockUserRepo.authenticate(request.email, request.password))
        .thenReturn(Future.successful(Some(deletedUser)))

      val result = router.userLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.PERMISSION_DENIED
        exception.getMessage should include("Account has been deactivated")
      }
    }

    "fail when trying to login with wrong auth provider" in {
      val googleUser = sampleUser.copy(auth_provider = "GOOGLE")
      val request = LoginRequest(
        email = "test@example.com",
        password = "password123"
      )

      when(mockUserRepo.authenticate(request.email, request.password))
        .thenReturn(Future.successful(Some(googleUser)))

      val result = router.userLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.PERMISSION_DENIED
        exception.getMessage should include("GOOGLE authentication")
      }
    }
  }

  "UserRouter#googleLogin" should {

    "login successfully with valid Google token (existing user)" in {
      val request = GoogleLoginRequest(idToken = "valid_google_token")
      val googleUserInfo = GoogleUserInfo(
        googleId = "google_12345",
        email = "google@example.com",
        name = "Google User"
      )
      val googleUser = sampleUser.copy(
        email = "google@example.com",
        username = "Google User",
        password = None,
        google_id = Some("google_12345"),
        auth_provider = "GOOGLE"
      )

      when(mockGoogleAuthService.verifyToken(request.idToken))
        .thenReturn(Future.successful(Some(googleUserInfo)))

      when(mockUserRepo.findUserByEmail(googleUserInfo.email))
        .thenReturn(Future.successful(Some(googleUser)))

      when(mockUserRepo.updateRefreshToken(any[Long](), any[String]()))
        .thenReturn(Future.successful(1))

      when(mockJwtUtility.createToken(googleUser.email, googleUser.role))
        .thenReturn("access_token_123")

      when(mockJwtUtility.createRefreshToken(googleUser.email, googleUser.role))
        .thenReturn("refresh_token_123")

      val result = router.googleLogin(request)
      whenReady(result) { response =>
        response.success shouldBe true
        response.isUserCreated shouldBe false
        response.accessToken shouldBe "access_token_123"
        response.refreshToken shouldBe "refresh_token_123"
        verify(mockGoogleAuthService).verifyToken(request.idToken)
        verify(mockUserRepo).findUserByEmail(googleUserInfo.email)
      }
    }

    "create new user and login with valid Google token (new user)" in {
      val request = GoogleLoginRequest(idToken = "valid_google_token")
      val googleUserInfo = GoogleUserInfo(
        googleId = "google_12345",
        email = "newgoogle@example.com",
        name = "New Google User"
      )

      when(mockGoogleAuthService.verifyToken(request.idToken))
        .thenReturn(Future.successful(Some(googleUserInfo)))

      when(mockUserRepo.findUserByEmail(googleUserInfo.email))
        .thenReturn(Future.successful(None))

      when(mockUserRepo.addUser(any[User]))
        .thenReturn(Future.successful(2L))

      when(mockJwtUtility.createToken(googleUserInfo.email, "USER"))
        .thenReturn("jwt_token_789")

      val result = router.googleLogin(request)

      whenReady(result) { response =>
        response.success shouldBe true
        response.isUserCreated shouldBe true
        response.accessToken shouldBe "jwt_token_789"
        response.message should include("Account created and login successful")
        verify(mockUserRepo).addUser(any[User])
      }
    }

    "fail when ID token is empty" in {
      val request = GoogleLoginRequest(idToken = "")

      val result = router.googleLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("ID token is required")
      }
    }

    "fail with invalid Google token" in {
      val request = GoogleLoginRequest(idToken = "invalid_token")

      when(mockGoogleAuthService.verifyToken(request.idToken))
        .thenReturn(Future.successful(None))

      val result = router.googleLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.UNAUTHENTICATED
        exception.getMessage should include("Invalid Google ID token")
      }
    }

    "fail when account is deactivated" in {
      val request = GoogleLoginRequest(idToken = "valid_google_token")
      val googleUserInfo = GoogleUserInfo(
        googleId = "google_12345",
        email = "deleted@example.com",
        name = "Deleted User"
      )
      val deletedUser = sampleUser.copy(
        email = "deleted@example.com",
        is_deleted = true,
        auth_provider = "GOOGLE"
      )

      when(mockGoogleAuthService.verifyToken(request.idToken))
        .thenReturn(Future.successful(Some(googleUserInfo)))

      when(mockUserRepo.findUserByEmail(googleUserInfo.email))
        .thenReturn(Future.successful(Some(deletedUser)))

      val result = router.googleLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.PERMISSION_DENIED
        exception.getMessage should include("Account has been deactivated")
      }
    }

    "fail when user exists with different auth provider" in {
      val request = GoogleLoginRequest(idToken = "valid_google_token")
      val googleUserInfo = GoogleUserInfo(
        googleId = "google_12345",
        email = "local@example.com",
        name = "Local User"
      )
      val localUser = sampleUser.copy(auth_provider = "LOCAL")

      when(mockGoogleAuthService.verifyToken(request.idToken))
        .thenReturn(Future.successful(Some(googleUserInfo)))

      when(mockUserRepo.findUserByEmail(googleUserInfo.email))
        .thenReturn(Future.successful(Some(localUser)))

      val result = router.googleLogin(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.ALREADY_EXISTS
        exception.getMessage should include("LOCAL authentication")
      }
    }
  }

  "UserRouter#getUserById" should {

    "return user when user exists" in {
      val request = GetUserRequest(id = 1L)

      when(mockUserRepo.getUserById(1L))
        .thenReturn(Future.successful(Some(sampleUser)))

      val result = router.getUserById(request)

      whenReady(result) { user =>
        user.id shouldBe sampleUser.id
        user.username shouldBe sampleUser.username
        user.email shouldBe sampleUser.email
        user.authProvider shouldBe AuthProvider.LOCAL
        verify(mockUserRepo).getUserById(1L)
      }
    }

    "fail when ID is invalid (0 or negative)" in {
      val request = GetUserRequest(id = 0L)

      val result = router.getUserById(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("ID should be valid")
      }
    }

    "fail when user not found" in {
      val request = GetUserRequest(id = 999L)

      when(mockUserRepo.getUserById(999L))
        .thenReturn(Future.successful(None))

      val result = router.getUserById(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception.asInstanceOf[GrpcServiceException].status.getCode shouldBe Status.Code.NOT_FOUND
        exception.getMessage should include("Unable to find User with Id: 999")
      }
    }

    "fail when user is deleted" in {
      val deletedUser = sampleUser.copy(is_deleted = true)
      val request = GetUserRequest(id = 1L)

      when(mockUserRepo.getUserById(1L))
        .thenReturn(Future.successful(Some(deletedUser)))

      val result = router.getUserById(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception.asInstanceOf[GrpcServiceException].status.getCode shouldBe Status.Code.NOT_FOUND
      }
    }
  }

  "UserRouter#deleteUserById" should {

    "delete user successfully" in {
      val request = DeleteUserRequest(id = 1L)

      when(mockUserRepo.deleteUserById(1L))
        .thenReturn(Future.successful(1))

      val result = router.deleteUserById(request)

      whenReady(result) { response =>
        response.id shouldBe 1L
        response.success shouldBe true
        verify(mockUserRepo).deleteUserById(1L)
      }
    }

    "fail when ID is invalid (0 or negative)" in {
      val request = DeleteUserRequest(id = -1L)

      val result = router.deleteUserById(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception
          .asInstanceOf[GrpcServiceException]
          .status
          .getCode shouldBe Status.Code.INVALID_ARGUMENT
        exception.getMessage should include("ID should be valid")
      }
    }

    "fail when user not found (0 rows affected)" in {
      val request = DeleteUserRequest(id = 999L)

      when(mockUserRepo.deleteUserById(999L))
        .thenReturn(Future.successful(0))

      val result = router.deleteUserById(request)

      whenReady(result.failed) { exception =>
        exception shouldBe a[GrpcServiceException]
        exception.asInstanceOf[GrpcServiceException].status.getCode shouldBe Status.Code.UNKNOWN
        exception.getMessage should include("Unable to User with Id: 999")
      }
    }
  }
}
