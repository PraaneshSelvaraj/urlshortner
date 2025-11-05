package controllers

import dtos.{LoginDTO, GoogleLoginDTO}
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import services.AuthService
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import io.grpc.StatusRuntimeException
import io.grpc.{Status => grpcStatus}
import org.apache.pekko.grpc.GrpcServiceException
import auth.{AuthenticatedAction, AuthenticatedRequest}
import play.mvc
import security.JwtUtility
import scala.util.{Failure, Success}

@Singleton
class AuthController @Inject() (
    val controllerComponents: ControllerComponents,
    val authService: AuthService,
    val authenticatedAction: AuthenticatedAction,
    val jwtUtility: JwtUtility
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
            (authService.login(userLoginData.email, userLoginData.password) map {
              case (accessToken, refreshToken) =>
                Ok(
                  Json.obj(
                    ("message", "Login was successfull"),
                    ("accessToken", accessToken),
                    ("refreshToken", refreshToken)
                  )
                )
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

  def refreshTokens(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    req.headers.get("Authorization") match {
      case Some(authHeader) if authHeader.startsWith("Bearer ") =>
        {
          val refreshToken = authHeader.substring("Bearer ".length).trim
          authService.refreshTokens(refreshToken).map { case (accessToken, refreshToken) =>
            Ok(
              Json.obj(
                ("message", "Tokens refreshed successfully."),
                ("accessToken", accessToken),
                ("refreshToken", refreshToken)
              )
            )
          }
        }.recover {
          case e: GrpcServiceException =>
            Unauthorized(
              Json.obj(("message", "Unable to authorize"), ("error", e.getStatus.getDescription))
            )
          case _ => InternalServerError((Json.obj(("error", "unable to refresh tokens"))))
        }
      case _ =>
        Future.successful(
          BadRequest(Json.obj(("error", "Missing or invalid Authorization Header")))
        )
    }
  }

  def logoutUser(): Action[AnyContent] = authenticatedAction.forUserOrAdmin.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      req.headers.get("Authorization") match {
        case Some(authHeader) if authHeader.startsWith("Bearer ") =>
          val token = authHeader.substring("Bearer ".length).trim

          jwtUtility.decodeToken(token) match {
            case Success(claims) =>
              jwtUtility.getJtiFromClaim(claims) match {
                case Success(jti) =>
                  val expiresAt = claims.expiration.getOrElse(0L)

                  authService.logoutUser(req.user.id, jti, expiresAt).map { success =>
                    if (success) {
                      Ok(
                        Json.obj(
                          "success" -> true,
                          "message" -> "Logout was successful."
                        )
                      )
                    } else {
                      InternalServerError(
                        Json.obj(
                          "success" -> false,
                          "message" -> "Logout failed."
                        )
                      )
                    }
                  }
                case Failure(_) =>
                  Future.successful(
                    Unauthorized(Json.obj("error" -> "Could not extract JTI from token"))
                  )
              }
            case Failure(ex) =>
              Future.successful(
                Unauthorized(Json.obj("error" -> s"Invalid token: ${ex.getMessage}"))
              )
          }
        case _ =>
          Future.successful(
            BadRequest(Json.obj("error" -> "Missing or invalid Authorization header"))
          )
      }
  }
}
