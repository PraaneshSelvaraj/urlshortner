package services

import dtos.CreateUserDTO
import play.api.Configuration
import example.urlshortner.user.grpc._
import example.urlshortner.notification.grpc._
import models.{User => UserModel}
import java.sql.Timestamp
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import org.mindrot.jbcrypt.BCrypt

class UserService @Inject() (
    userServiceClient: UserServiceClient,
    notificationServiceClient: NotificationServiceClient,
    config: Configuration
)(implicit ec: ExecutionContext) {

  private val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
  private val usernameRegex = """^[a-zA-Z][a-zA-Z0-9]*$""".r
  private val passwordRegex =
    """^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).+$""".r

  def addUser(userDTO: CreateUserDTO, isOauthUser: Boolean = false): Future[UserModel] = {
    if (userDTO.username.length < 3) {
      return Future.failed(
        new IllegalArgumentException("Username must be at least 3 characters long")
      )
    }
    if (!usernameRegex.matches(userDTO.username)) {
      return Future.failed(
        new IllegalArgumentException(
          "Username must start with a letter and contain only alphanumeric characters"
        )
      )
    }

    if (!emailRegex.matches(userDTO.email)) {
      return Future.failed(new IllegalArgumentException("Invalid email format"))
    }

    if (!isOauthUser && userDTO.password.length < 8) {
      return Future.failed(
        new IllegalArgumentException("Password must be at least 8 characters long")
      )
    }
    if (!isOauthUser && !passwordRegex.matches(userDTO.password)) {
      return Future.failed(
        new IllegalArgumentException(
          "Password must contain at least one letter, one number, and one special character"
        )
      )
    }

    val userRole = userDTO.role match {
      case Some(role) if role == "ADMIN" => UserRole.ADMIN
      case _                             => UserRole.USER
    }
    val createUser = CreateUserRequest(
      username = userDTO.username,
      email = userDTO.email,
      password = BCrypt.hashpw(userDTO.password, BCrypt.gensalt()),
      userRole = Some(userRole)
    )

    for {
      createdUser <- userServiceClient.createUser(createUser)
      _ <- {
        val notification = NotificationRequest(
          notificationType = NotificationType.NEWUSER,
          message = s"User Created with Id: ${createdUser.id}",
          userId = Some(createdUser.id)
        )
        val reply = notificationServiceClient.notifyMethod(notification)
        reply.map(r =>
          println(
            s"Notification Success: ${r.success}, Notification Status: ${r.notificationStatus}  Notification Message: ${r.message}"
          )
        )
      }
    } yield {
      UserModel(
        id = createdUser.id,
        username = createdUser.username,
        email = createdUser.email,
        password = createdUser.password,
        role = createdUser.role.toString(),
        google_id = createdUser.googleId,
        auth_provider = createdUser.authProvider.toString(),
        refresh_token = createdUser.refreshToken,
        is_deleted = createdUser.isDeleted,
        created_at = new Timestamp(createdUser.createdAt),
        updated_at = new Timestamp(createdUser.updatedAt)
      )
    }
  }

  def getUserById(id: Long): Future[UserModel] = {
    val getUserRequest = GetUserRequest(
      id = id
    )
    for {
      user <- userServiceClient.getUserById(getUserRequest)
    } yield {
      UserModel(
        id = user.id,
        username = user.username,
        email = user.email,
        password = user.password,
        role = user.role.toString(),
        google_id = user.googleId,
        auth_provider = user.authProvider.toString(),
        refresh_token = user.refreshToken,
        is_deleted = user.isDeleted,
        created_at = new Timestamp(user.createdAt),
        updated_at = new Timestamp(user.updatedAt)
      )
    }
  }

  def deleteUserById(id: Long): Future[Boolean] = {
    val deleteUserRequest = DeleteUserRequest(
      id = id
    )

    for {
      reply <- userServiceClient.deleteUserById(deleteUserRequest)
    } yield reply.success
  }
}
