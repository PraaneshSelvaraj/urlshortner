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
)(implicit val executionContext: ExecutionContext) {

  def apply(allowedRoles: Set[String]): ActionBuilder[AuthenticatedRequest, AnyContent] =
    new AuthenticatedActionImpl(allowedRoles)

  def forUser: ActionBuilder[AuthenticatedRequest, AnyContent] = apply(Set("USER"))
  def forAdmin: ActionBuilder[AuthenticatedRequest, AnyContent] = apply(Set("ADMIN"))

  def forUserOrAdmin: ActionBuilder[AuthenticatedRequest, AnyContent] = apply(Set("USER", "ADMIN"))

  private class AuthenticatedActionImpl(allowedRoles: Set[String])
      extends ActionBuilder[AuthenticatedRequest, AnyContent] {

    override def parser: BodyParsers.Default = AuthenticatedAction.this.parser
    override def executionContext: ExecutionContext = AuthenticatedAction.this.executionContext

    override def invokeBlock[A](
        request: Request[A],
        block: AuthenticatedRequest[A] => Future[Result]
    ): Future[Result] = {

      implicit val ec: ExecutionContext = executionContext

      request.headers.get(AUTHORIZATION) match {
        case Some(header) if header.startsWith("Bearer ") =>
          val token = header.substring(7)
          jwtUtility.decodeToken(token) match {
            case Success(claims) =>
              jwtUtility.getClaimsData(claims) match {
                case Some((email, role)) =>
                  userRepo.findUserByEmail(email) flatMap {
                    case Some(user) =>
                      if (user.is_deleted) {
                        Future.successful(
                          Forbidden(
                            Json.obj("message" -> "Invalid token: User not found or deactivated")
                          )
                        )
                      } else if (allowedRoles.contains(user.role)) {
                        block(AuthenticatedRequest(user, request))
                      } else {
                        Future.successful(
                          Forbidden(
                            Json.obj(
                              "message" -> s"Access denied: User role '${user.role}' is not authorized"
                            )
                          )
                        )
                      }
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
                      Json.obj(
                        "message" -> "Invalid token: Missing required claims (username/role)"
                      )
                    )
                  )
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
              Json.obj("message" -> "Authorization header malformed: Expected 'Bearer <token>'")
            )
          )
        case None =>
          Future.successful(
            Unauthorized(Json.obj("message" -> "Authorization header missing or invalid"))
          )
      }
    }
  }
}
