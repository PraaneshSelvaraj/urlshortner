package auth

import play.api.mvc._
import play.api.mvc.Results._
import play.api.http.HeaderNames._
import repositories.UserRepo
import security.JwtUtility
import play.api.libs.json.Json
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import pdi.jwt.exceptions.{JwtExpirationException, JwtValidationException}

@Singleton class AuthenticatedAction @Inject() (
    val parser: BodyParsers.Default,
    jwtUtility: JwtUtility,
    userRepo: UserRepo
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = request.headers.get(AUTHORIZATION) match {
    case Some(header) if header.startsWith("Bearer ") =>
      val token = header.substring(7)
      jwtUtility.decodeToken(token) match {
        case Success(claims) => {
          jwtUtility.getClaimsData(claims) match {
            case Some((email, role)) =>
              userRepo.findUserByEmail(email) flatMap {
                case Some(user) if user.role == "USER" =>
                  block(AuthenticatedRequest(user, request))
                case Some(user) =>
                  Future.successful(
                    Forbidden(
                      Json.obj(
                        "message" -> s"Access denied: User role '${user.role}' is not authorized"
                      )
                    )
                  )
                case None =>
                  Future.successful(
                    Unauthorized(
                      Json.obj("message" -> "Invalid token: User not found or deactivated")
                    )
                  )
              }
            case None =>
              Future.successful(
                Unauthorized(
                  Json.obj("message" -> "Invalid token: Missing required claims (username/role)")
                )
              )
          }
        }
        case Failure(ex) =>
          ex match {
            case e: JwtExpirationException =>
              Future.successful(
                Unauthorized(Json.obj("message" -> "Token expired", "error" -> e.getMessage))
              )
            case e: JwtValidationException =>
              Future.successful(
                Unauthorized(
                  Json.obj(
                    "message" -> "Invalid token signature or format",
                    "error" -> e.getMessage
                  )
                )
              )
            case _ =>
              Future.successful(
                Unauthorized(Json.obj("message" -> s"Invalid token: ${ex.getMessage}"))
              )
          }
      }
    case Some(_) =>
      Future.successful(
        Unauthorized(
          Json.obj("message" -> s"Authorization header malformed: Expected 'Bearer <token>'")
        )
      )
    case None =>
      Future.successful(Unauthorized("Authorization header missing or invalid"))
  }
}
