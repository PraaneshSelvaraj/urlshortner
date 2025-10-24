package helpers

import auth.{AuthenticatedRequest, AuthenticatedAction}
import models.User
import play.api.mvc._
import repositories.UserRepo
import security.JwtUtility
import scala.concurrent.{ExecutionContext, Future}
import java.sql.Timestamp
import java.time.Instant

class StubAuthenticatedAction(
    override val parser: BodyParsers.Default,
    jwtUtility: JwtUtility,
    userRepo: UserRepo,
    shouldAuthenticate: Boolean = true,
    userRole: String = "USER",
    userId: Long = 1L
)(implicit override val executionContext: ExecutionContext)
    extends AuthenticatedAction(parser, jwtUtility, userRepo) {

  private val stubUser = User(
    id = userId,
    username = "testuser",
    email = "test@example.com",
    password = Some("hashedPassword"),
    role = userRole,
    google_id = None,
    auth_provider = "LOCAL",
    refresh_token = None,
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  override def apply(allowedRoles: Set[String]): ActionBuilder[AuthenticatedRequest, AnyContent] =
    new StubActionBuilder(allowedRoles)

  override def forUser: ActionBuilder[AuthenticatedRequest, AnyContent] =
    new StubActionBuilder(Set("USER"))

  override def forAdmin: ActionBuilder[AuthenticatedRequest, AnyContent] =
    new StubActionBuilder(Set("ADMIN"))

  override def forUserOrAdmin: ActionBuilder[AuthenticatedRequest, AnyContent] =
    new StubActionBuilder(Set("USER", "ADMIN"))

  private class StubActionBuilder(allowedRoles: Set[String])
      extends ActionBuilder[AuthenticatedRequest, AnyContent] {

    override def parser: BodyParsers.Default = StubAuthenticatedAction.this.parser
    override def executionContext: ExecutionContext = StubAuthenticatedAction.this.executionContext

    override def invokeBlock[A](
        request: Request[A],
        block: AuthenticatedRequest[A] => Future[Result]
    ): Future[Result] = {
      if (shouldAuthenticate && allowedRoles.contains(userRole)) {
        block(AuthenticatedRequest(stubUser, request))
      } else if (!shouldAuthenticate) {
        Future.successful(Results.Unauthorized("Unauthorized"))
      } else {
        Future.successful(Results.Forbidden("Forbidden"))
      }
    }
  }
}
