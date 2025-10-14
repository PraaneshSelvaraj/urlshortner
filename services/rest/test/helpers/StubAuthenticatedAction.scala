package helpers

import auth.{AuthenticatedAction, AuthenticatedRequest}
import models.User
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json.Json
import repositories.UserRepo
import security.JwtUtility
import scala.concurrent.{ExecutionContext, Future}
import java.sql.Timestamp
import java.time.Instant

class StubAuthenticatedAction(
    parser: BodyParsers.Default,
    jwtUtility: JwtUtility,
    userRepo: UserRepo,
    shouldAuthenticate: Boolean = true,
    userRole: String = "USER",
    userId: Long = 1L,
    errorType: Option[String] = None
)(implicit ec: ExecutionContext)
    extends AuthenticatedAction(
      parser,
      jwtUtility,
      userRepo
    )(ec) {

  override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {

    if (shouldAuthenticate) {
      val testUser = User(
        id = userId,
        username = "testuser",
        email = "test@example.com",
        password = Some("hashedPassword123"),
        role = userRole,
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      if (testUser.role == "USER") {
        val authRequest = AuthenticatedRequest(testUser, request)
        block(authRequest)
      } else {
        Future.successful(
          Forbidden(
            Json.obj(
              "message" -> s"Access denied: User role '${testUser.role}' is not authorized"
            )
          )
        )
      }

    } else {
      errorType match {
        case Some("invalid_token") =>
          Future.successful(
            Unauthorized(
              Json.obj("message" -> "Invalid token: User not found or deactivated")
            )
          )
        case Some("missing_token") =>
          Future.successful(
            Unauthorized("Authorization header missing or invalid")
          )
        case Some("expired_token") =>
          Future.successful(
            Unauthorized(
              Json.obj("message" -> "Token expired", "error" -> "Token has expired")
            )
          )
        case Some("malformed_header") =>
          Future.successful(
            Unauthorized(
              Json.obj("message" -> "Authorization header malformed: Expected 'Bearer <token>'")
            )
          )
        case Some("missing_claims") =>
          Future.successful(
            Unauthorized(
              Json.obj("message" -> "Invalid token: Missing required claims (username/role)")
            )
          )
        case _ =>
          Future.successful(
            Unauthorized(Json.obj("message" -> "Unauthorized"))
          )
      }
    }
  }
}
