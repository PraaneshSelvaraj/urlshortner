package services

import dtos.LoginDTO
import example.urlshortner.user.grpc.{
  UserServiceClient,
  LoginRequest,
  GoogleLoginRequest,
  LoginResponse,
  UserRole,
  AuthProvider,
  User
}
import example.urlshortner.notification.grpc.{
  NotificationServiceClient,
  NotificationRequest,
  NotificationReply,
  NotificationType
}
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span, Milliseconds}
import play.api.libs.json.{Json, JsObject}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

class AuthServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val mockUserServiceClient = mock[UserServiceClient]
  private val mockNotificationServiceClient = mock[NotificationServiceClient]
  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Milliseconds)
  )

  private val authService =
    new AuthService(mockUserServiceClient, mockNotificationServiceClient)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserServiceClient, mockNotificationServiceClient)
  }

  "AuthService#login" should {

    "return token for successful login" in {
      val email = "test@example.com"
      val password = "secret"
      val expectedToken = "test.jwt.token"
      val refreshToken = "test.jwt.refresh"
      val loginResponse =
        LoginResponse(
          success = true,
          message = "OK",
          accessToken = expectedToken,
          refreshToken = refreshToken,
          user = None
        )

      when(mockUserServiceClient.userLogin(any[LoginRequest]()))
        .thenReturn(Future.successful(loginResponse))

      val futureResult = authService.login(email, password)

      whenReady(futureResult) { case (access, refresh) =>
        access mustBe expectedToken
        refresh mustBe refreshToken
        verify(mockUserServiceClient).userLogin(any[LoginRequest]())
      }
    }

    "fail when user service returns unsuccessful response" in {
      val email = "fail@example.com"
      val password = "incorrect"
      val errorMessage = "Invalid credentials"
      val loginResponse =
        LoginResponse(
          success = false,
          message = errorMessage,
          accessToken = "",
          refreshToken = "",
          user = None
        )

      when(mockUserServiceClient.userLogin(any[LoginRequest]()))
        .thenReturn(Future.successful(loginResponse))

      val futureResult = authService.login(email, password)

      whenReady(futureResult.failed) { ex =>
        ex.getMessage must include(errorMessage)
        verify(mockUserServiceClient).userLogin(any[LoginRequest]())
      }
    }

    "fail when user service throws exception" in {
      val email = "error@example.com"
      val password = "password"

      when(mockUserServiceClient.userLogin(any[LoginRequest]()))
        .thenReturn(Future.failed(new RuntimeException("Service down")))

      val futureResult = authService.login(email, password)

      whenReady(futureResult.failed) { ex =>
        ex mustBe a[RuntimeException]
      }
    }
  }

  "AuthService#googleLogin" should {

    "return user info and send notification when new user is created" in {
      val idToken = "sampleIdToken"
      val user = User(
        id = 101L,
        username = "googleuser",
        email = "googleuser@example.com",
        password = None,
        role = UserRole.USER,
        googleId = Some("google_123"),
        authProvider = AuthProvider.GOOGLE,
        isDeleted = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
      )
      val loginResponse = LoginResponse(
        success = true,
        message = "User successfully created via Google",
        accessToken = "jwt.token.access",
        refreshToken = "jwt.token.refresh",
        user = Some(user),
        isUserCreated = true
      )
      val notificationReply = NotificationReply(success = true, message = "Notification sent")

      when(mockUserServiceClient.googleLogin(any[GoogleLoginRequest]()))
        .thenReturn(Future.successful(loginResponse))
      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]()))
        .thenReturn(Future.successful(notificationReply))

      val futureResult = authService.googleLogin(idToken)

      whenReady(futureResult) { json =>
        (json \ "success").as[Boolean] mustBe true
        (json \ "user" \ "id").as[Long] mustBe 101L
        verify(mockUserServiceClient).googleLogin(any[GoogleLoginRequest]())
      }
    }

    "return only token if user object is None" in {
      val idToken = "noUserToken"
      val loginResponse = LoginResponse(
        success = true,
        message = "Login Success",
        accessToken = "jwt.token.access",
        refreshToken = "jwt.token.refresh",
        user = None,
        isUserCreated = false
      )

      when(mockUserServiceClient.googleLogin(any[GoogleLoginRequest]()))
        .thenReturn(Future.successful(loginResponse))

      val futureResult = authService.googleLogin(idToken)

      whenReady(futureResult) { json =>
        (json \ "success").as[Boolean] mustBe true
        (json \ "accessToken").as[String] mustBe "jwt.token.access"
        (json \ "refreshToken").as[String] mustBe "jwt.token.refresh"
        (json \ "user").asOpt[JsObject] mustBe None
      }
    }

    "not send notification when isUserCreated is false" in {
      val idToken = "existingUserToken"
      val user = User(
        id = 1L,
        username = "oldGoogleUser",
        email = "old@example.com",
        password = None,
        role = UserRole.USER,
        googleId = Some("some_id"),
        authProvider = AuthProvider.GOOGLE,
        refreshToken = None,
        isDeleted = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
      )
      val loginResponse = LoginResponse(
        success = true,
        message = "Login again",
        accessToken = "existing.token.access",
        refreshToken = "existing.token.refresh",
        user = Some(user),
        isUserCreated = false
      )

      when(mockUserServiceClient.googleLogin(any[GoogleLoginRequest]()))
        .thenReturn(Future.successful(loginResponse))

      val futureResult = authService.googleLogin(idToken)
      whenReady(futureResult) { json =>
        (json \ "success").as[Boolean] mustBe true
        (json \ "accessToken").as[String] mustBe "existing.token.access"
        (json \ "refreshToken").as[String] mustBe "existing.token.refresh"
        verify(mockUserServiceClient).googleLogin(any[GoogleLoginRequest]())
      }
    }

    "propagate failure if userServiceClient.googleLogin fails" in {
      val idToken = "failToken"
      when(mockUserServiceClient.googleLogin(any[GoogleLoginRequest]()))
        .thenReturn(Future.failed(new RuntimeException("Google login error")))

      val futureResult = authService.googleLogin(idToken)

      whenReady(futureResult.failed) { ex =>
        ex mustBe a[RuntimeException]
      }
    }
  }

  "AuthService#isNewUserCreation" should {

    "return true for messages containing 'created'" in {
      authService.isNewUserCreation("User has been created") mustBe true
      authService.isNewUserCreation("created successfully") mustBe true
    }

    "return false for other messages" in {
      authService.isNewUserCreation("Login successful") mustBe false
      authService.isNewUserCreation("User already exists") mustBe false
    }
  }
}
