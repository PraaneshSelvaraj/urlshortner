package controllers

import dtos.{LoginDTO, GoogleLoginDTO}
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import services.AuthService
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import io.grpc.StatusRuntimeException
import io.grpc.{Status => grpcStatus}

@Singleton
class AuthController @Inject() (
    val controllerComponents: ControllerComponents,
    val authService: AuthService
)(implicit ec: ExecutionContext)
    extends BaseController {

  def login: Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    req.body.asJson match {
      case Some(jsonData) =>
        jsonData.validate[LoginDTO].asEither match {
          case Left(errors) =>
            Future.successful(
              BadRequest(
                Json.obj(
                  ("message", "Invalid JSON for Login Schema"),
                  ("errors", JsError.toJson(errors))
                )
              )
            )
          case Right(userLoginData) =>
            (authService.login(userLoginData.email, userLoginData.password) map { token =>
              Ok(Json.obj(("message", "Login was successfull"), ("token", token)))
            }).recover {
              case ex: StatusRuntimeException
                  if ex.getStatus.getCode == grpcStatus.Code.PERMISSION_DENIED =>
                Unauthorized(
                  Json.obj(
                    ("message", "Failed to login"),
                    ("reason", "Invalid Username or Password")
                  )
                )

              case ex: StatusRuntimeException =>
                InternalServerError(s"gRPC error: ${ex.getStatus.getCode} - ${ex.getMessage}")

              case ex: Exception =>
                InternalServerError(s"ERROR OCCURRED: ${ex.getMessage}, ${ex.getClass}")
            }
        }
      case None => Future.successful(BadRequest(Json.obj("message" -> "Expecting JSON Body")))
    }
  }

  def googleLogin: Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    req.body.asJson match {
      case Some(jsonData) =>
        jsonData.validate[GoogleLoginDTO].asEither match {
          case Left(errors) =>
            Future.successful(
              BadRequest(
                Json.obj(
                  ("message", "Invalid JSON for Google Login Schema"),
                  ("errors", JsError.toJson(errors))
                )
              )
            )
          case Right(googleLoginData) =>
            (authService.googleLogin(googleLoginData.idToken) map { responseJson =>
              val message = (responseJson \ "message").as[String]
              val statusCode = if (authService.isNewUserCreation(message)) Created else Ok

              statusCode(responseJson)
            }).recover {
              case ex: StatusRuntimeException
                  if ex.getStatus.getCode == grpcStatus.Code.UNAUTHENTICATED =>
                Unauthorized(
                  Json.obj(
                    ("success", false),
                    ("message", "Invalid or expired Google ID token")
                  )
                )

              case ex: StatusRuntimeException
                  if ex.getStatus.getCode == grpcStatus.Code.ALREADY_EXISTS =>
                Conflict(
                  Json.obj(
                    ("success", false),
                    ("message", ex.getStatus.getDescription)
                  )
                )

              case ex: StatusRuntimeException
                  if ex.getStatus.getCode == grpcStatus.Code.PERMISSION_DENIED =>
                Forbidden(
                  Json.obj(
                    ("success", false),
                    ("message", ex.getStatus.getDescription)
                  )
                )

              case ex: StatusRuntimeException =>
                InternalServerError(
                  Json.obj(
                    ("success", false),
                    ("message", s"gRPC error: ${ex.getStatus.getCode} - ${ex.getMessage}")
                  )
                )

              case ex: Exception =>
                InternalServerError(
                  Json.obj(
                    ("success", false),
                    ("message", s"Authentication failed: ${ex.getMessage}")
                  )
                )
            }
        }
      case None =>
        Future.successful(
          BadRequest(Json.obj(("message", "Expecting JSON Body with idToken field")))
        )
    }
  }
}
