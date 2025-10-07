package auth

import play.api.mvc.{Request, WrappedRequest}
import models.User

case class AuthenticatedRequest[A](user: User, request: Request[A])
    extends WrappedRequest[A](request)
