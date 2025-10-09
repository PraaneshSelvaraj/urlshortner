package services

import dtos.LoginDTO
import example.urlshortner.user.grpc.{
  UserServiceClient,
  LoginRequest,
  GoogleLoginRequest,
  LoginResponse
}
import example.urlshortner.notification.grpc._
import play.api.libs.json.{Json, JsObject}

import com.google.protobuf.empty.Empty
import exceptions.{TresholdReachedException, UrlExpiredException}
import java.time.{Instant, Duration}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class AuthService @Inject() (
    userServiceClient: UserServiceClient,
    notificationServiceClient: NotificationServiceClient
)(implicit ec: ExecutionContext) {

  def login(email: String, password: String): Future[String] = {
    val loginRequest = LoginRequest(email, password)
    for {
      reply <- userServiceClient.userLogin(loginRequest)
      token <-
        if (reply.success) {
          Future.successful(reply.token)
        } else {
          Future.failed(new Exception(reply.message))
        }
    } yield token
  }

  def googleLogin(idToken: String): Future[JsObject] = {
    val googleLoginRequest = GoogleLoginRequest(idToken = idToken)

    userServiceClient.googleLogin(googleLoginRequest).map { response =>
      if (response.isUserCreated) {
        val userId = response.user match {
          case Some(user) => user.id
          case None       => 0L
        }
        val notification = NotificationRequest(
          notificationType = NotificationType.NEWUSER,
          message = s"User Created with Id: ${userId}",
          userId = Some(userId)
        )
        val reply = notificationServiceClient.notifyMethod(notification)
        reply.map(r =>
          println(
            s"Notification Success: ${r.success}, Notification Status: ${r.notificationStatus}  Notification Message: ${r.message}"
          )
        )
      }
      response.user match {
        case Some(user) =>
          Json.obj(
            "success" -> true,
            "message" -> response.message,
            "token" -> response.token,
            "user" -> Json.obj(
              "id" -> user.id,
              "username" -> user.username,
              "email" -> user.email,
              "role" -> user.role,
              "authProvider" -> (if (user.authProvider.isGoogle) "GOOGLE" else "LOCAL")
            )
          )
        case None =>
          Json.obj(
            "success" -> true,
            "message" -> response.message,
            "token" -> response.token
          )
      }
    }
  }

  def isNewUserCreation(message: String): Boolean = {
    message.toLowerCase.contains("created")
  }
}
