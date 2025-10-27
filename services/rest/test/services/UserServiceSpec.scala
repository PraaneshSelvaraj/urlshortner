package services

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import repositories.UserRepo
import example.urlshortner.notification.grpc.{
  GetNotificationsResponse,
  NotificationReply,
  NotificationRequest,
  NotificationServiceClient,
  NotificationType
}
import example.urlshortner.user.grpc.{
  UserRole,
  UserServiceClient,
  CreateUserRequest,
  User,
  AuthProvider,
  GetUserRequest,
  DeleteUserRequest,
  DeleteUserResponse
}
import com.google.protobuf.empty.Empty
import org.scalatest.time.{Seconds, Span, Milliseconds}
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import org.mindrot.jbcrypt.BCrypt
import models.{User => UserModel}
import dtos.CreateUserDTO
import io.grpc.StatusRuntimeException
import io.grpc.Status

class UserServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val mockUserRepo: UserRepo = mock[UserRepo]
  private val mockNotificationServiceClient: NotificationServiceClient =
    mock[NotificationServiceClient]
  private val mockUserServiceClient: UserServiceClient = mock[UserServiceClient]
  private val mockConfig: Configuration = mock[Configuration]

  private val userService =
    new UserService(mockUserServiceClient, mockNotificationServiceClient, mockConfig)

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Milliseconds)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserRepo, mockNotificationServiceClient, mockUserServiceClient, mockConfig)
  }

  private val testPassword = "SecurePassword123"
  private val hashedPassword = BCrypt.hashpw(testPassword, BCrypt.gensalt())

  private val sampleUserModel = UserModel(
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

  private val sampleGrpcUser = User(
    id = sampleUserModel.id,
    username = sampleUserModel.username,
    email = sampleUserModel.email,
    password = sampleUserModel.password,
    role = sampleUserModel.role match {
      case "ADMIN" => UserRole.ADMIN
      case "USER"  => UserRole.USER
    },
    googleId = sampleUserModel.google_id,
    authProvider = AuthProvider.LOCAL,
    isDeleted = sampleUserModel.is_deleted,
    createdAt = sampleUserModel.created_at.getTime,
    updatedAt = sampleUserModel.updated_at.getTime
  )

  "UserService#addUser" should {

    "create a new user and send notification successfully" in {
      val userDTO = CreateUserDTO(
        username = "newuser",
        email = "newuser@example.com",
        password = hashedPassword,
        role = Some("USER")
      )

      when(mockUserServiceClient.createUser(any[CreateUserRequest]()))
        .thenReturn(Future.successful(sampleGrpcUser))

      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]()))
        .thenReturn(
          Future.successful(
            NotificationReply(success = true, message = "Notification sent")
          )
        )

      val result = userService.addUser(userDTO)

      whenReady(result) { userAdded =>
        userAdded.email mustBe sampleUserModel.email
        userAdded.username mustBe sampleUserModel.username
        userAdded.id mustBe sampleUserModel.id
        verify(mockUserServiceClient).createUser(any[CreateUserRequest]())
        verify(mockNotificationServiceClient).notifyMethod(any[NotificationRequest]())
      }
    }

    "fail when user service throws exception" in {
      val userDTO = CreateUserDTO(
        username = "newuser",
        email = "newuser@example.com",
        password = hashedPassword,
        role = Some("USER")
      )

      when(mockUserServiceClient.createUser(any[CreateUserRequest]()))
        .thenReturn(Future.failed(new StatusRuntimeException(Status.ALREADY_EXISTS)))

      val result = userService.addUser(userDTO)

      whenReady(result.failed) { exception =>
        exception mustBe a[StatusRuntimeException]
      }
    }

    "create user with Google OAuth credentials" in {
      val googleUser = CreateUserDTO(
        username = "googleuser",
        email = "google@example.com",
        password = "", // No password for OAuth
        role = Some("USER")
      )

      val grpcGoogleUser = User(
        id = 2L,
        username = "googleuser",
        email = "google@example.com",
        password = None,
        role = UserRole.USER,
        googleId = Some("google_12345"),
        authProvider = AuthProvider.GOOGLE,
        isDeleted = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
      )

      when(mockUserServiceClient.createUser(any[CreateUserRequest]()))
        .thenReturn(Future.successful(grpcGoogleUser))

      when(mockNotificationServiceClient.notifyMethod(any[NotificationRequest]()))
        .thenReturn(
          Future.successful(
            NotificationReply(success = true, message = "Notification sent")
          )
        )

      val result = userService.addUser(googleUser, true)

      whenReady(result) { userAdded =>
        userAdded.email mustBe "google@example.com"
        userAdded.auth_provider mustBe "GOOGLE"
        userAdded.google_id mustBe Some("google_12345")
      }
    }
  }

  "UserService#getUserById" should {

    "return user when user exists" in {
      when(mockUserServiceClient.getUserById(any[GetUserRequest]()))
        .thenReturn(Future.successful(sampleGrpcUser))

      val result = userService.getUserById(1L)

      whenReady(result) { userFetched =>
        userFetched.id mustBe sampleUserModel.id
        userFetched.email mustBe sampleUserModel.email
        userFetched.username mustBe sampleUserModel.username
        verify(mockUserServiceClient).getUserById(any[GetUserRequest]())
      }
    }

    "fail when user not found" in {
      when(mockUserServiceClient.getUserById(any[GetUserRequest]()))
        .thenReturn(Future.failed(new StatusRuntimeException(Status.NOT_FOUND)))

      val result = userService.getUserById(999L)

      whenReady(result.failed) { exception =>
        exception mustBe a[StatusRuntimeException]
      }
    }

    "fail when gRPC service throws exception" in {
      when(mockUserServiceClient.getUserById(any[GetUserRequest]()))
        .thenReturn(Future.failed(new StatusRuntimeException(Status.INTERNAL)))

      val result = userService.getUserById(1L)

      whenReady(result.failed) { exception =>
        exception mustBe a[StatusRuntimeException]
      }
    }

    "correctly convert timestamps from gRPC to UserModel" in {
      val now = System.currentTimeMillis()
      val grpcUserWithTimestamp = sampleGrpcUser.copy(
        createdAt = now,
        updatedAt = now
      )

      when(mockUserServiceClient.getUserById(any[GetUserRequest]()))
        .thenReturn(Future.successful(grpcUserWithTimestamp))

      val result = userService.getUserById(1L)

      whenReady(result) { userFetched =>
        userFetched.created_at.getTime mustBe now
        userFetched.updated_at.getTime mustBe now
      }
    }
  }

  "UserService#deleteUserById" should {

    "successfully delete user" in {
      when(mockUserServiceClient.deleteUserById(any[DeleteUserRequest]()))
        .thenReturn(Future.successful(DeleteUserResponse(id = 1L, success = true)))

      val result = userService.deleteUserById(1L)

      whenReady(result) { success =>
        success mustBe true
        verify(mockUserServiceClient).deleteUserById(any[DeleteUserRequest]())
      }
    }

    "return false when user not found" in {
      when(mockUserServiceClient.deleteUserById(any[DeleteUserRequest]()))
        .thenReturn(Future.successful(DeleteUserResponse(id = 999L, success = false)))

      val result = userService.deleteUserById(999L)

      whenReady(result) { success =>
        success mustBe false
      }
    }

    "fail when gRPC service throws exception" in {
      when(mockUserServiceClient.deleteUserById(any[DeleteUserRequest]()))
        .thenReturn(Future.failed(new StatusRuntimeException(Status.INTERNAL)))

      val result = userService.deleteUserById(1L)

      whenReady(result.failed) { exception =>
        exception mustBe a[StatusRuntimeException]
      }
    }

    "fail when user is not found (INVALID_ARGUMENT)" in {
      when(mockUserServiceClient.deleteUserById(any[DeleteUserRequest]()))
        .thenReturn(Future.failed(new StatusRuntimeException(Status.INVALID_ARGUMENT)))

      val result = userService.deleteUserById(999L)

      whenReady(result.failed) { exception =>
        exception mustBe a[StatusRuntimeException]
        exception
          .asInstanceOf[StatusRuntimeException]
          .getStatus
          .getCode mustBe Status.Code.INVALID_ARGUMENT
      }
    }
  }
}
