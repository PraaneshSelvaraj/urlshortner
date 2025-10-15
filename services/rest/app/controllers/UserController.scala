package controllers

import play.api.libs.json.Json
import play.api.mvc._
import dtos.CreateUserDTO
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import dtos.CreateUserDTO
import services.UserService
import java.sql.SQLException
import io.grpc.StatusRuntimeException
import io.grpc.{Status => grpcStatus}
import org.mindrot.jbcrypt.BCrypt
import auth.AuthenticatedAction
import auth.AuthenticatedRequest

class UserController @Inject() (
    val userService: UserService,
    val controllerComponents: ControllerComponents,
    val authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def addUser(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    req.body.asJson match {
      case Some(jsonData) =>
        jsonData.validate[CreateUserDTO].asOpt match {
          case Some(userData) =>
            userService
              .addUser(userData.copy(password = BCrypt.hashpw(userData.password, BCrypt.gensalt())))
              .map(userCreated => Ok(Json.obj(("message", "User Created"), ("data", userCreated))))
              .recover {
                case e: StatusRuntimeException
                    if e.getStatus.getCode == grpcStatus.Code.ALREADY_EXISTS =>
                  Conflict(s"Username or Email already exists")

                case e: StatusRuntimeException =>
                  InternalServerError(s"gRPC error: ${e.getStatus.getCode} - ${e.getMessage}")

                case e: Exception =>
                  InternalServerError(s"ERROR OCCURRED: ${e.getMessage}, ${e.getClass}")
              }
          case None =>
            Future.successful(BadRequest(Json.obj(("message", "Invalid Request Body Schema"))))
        }
      case None =>
        Future.successful(BadRequest(Json.obj(("message", "Request Body needs to be JSON"))))
    }
  }

  def getUserById(id: Long): Action[AnyContent] = authenticatedAction.forUser.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      userService
        .getUserById(id)
        .map(user =>
          Ok(Json.obj(("message", s"User with Id: ${user.id} was fetched."), ("data", user)))
        )
        .recover {
          case e: StatusRuntimeException
              if e.getStatus.getCode == grpcStatus.Code.INVALID_ARGUMENT =>
            NotFound(s"Unable to find User with Id: $id")

          case e: StatusRuntimeException if e.getStatus.getCode == grpcStatus.Code.NOT_FOUND =>
            NotFound(s"Unable to find User with Id: $id")

          case e: StatusRuntimeException =>
            InternalServerError(s"gRPC error: ${e.getStatus.getCode} - ${e.getMessage}")

          case e: Exception =>
            InternalServerError(s"ERROR OCCURRED: ${e.getMessage}, ${e.getClass}")
        }
  }

  def deleteUserById(id: Long): Action[AnyContent] = authenticatedAction.forUser.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      if (req.user.id != id) {
        Future.successful(
          Forbidden(Json.obj(("message", "You can only delete your own account")))
        )
      } else {
        userService
          .deleteUserById(id)
          .map(success =>
            if (success) NoContent
            else NotFound(Json.obj(("message", s"Unalbe to delete User with Id: $id")))
          )
          .recover {
            case e: StatusRuntimeException
                if e.getStatus.getCode == grpcStatus.Code.INVALID_ARGUMENT =>
              NotFound(s"Unable to delete User with Id: $id")

            case e: StatusRuntimeException if e.getStatus.getCode == grpcStatus.Code.UNKNOWN =>
              NotFound(s"Unable to delete User with Id: $id")

            case e: StatusRuntimeException =>
              InternalServerError(s"gRPC error: ${e.getStatus.getCode} - ${e.getMessage}")

            case e: Exception =>
              InternalServerError(s"ERROR OCCURRED: ${e.getMessage}, ${e.getClass}")
          }
      }
  }
}
