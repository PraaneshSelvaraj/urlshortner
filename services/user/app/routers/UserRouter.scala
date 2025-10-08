package routers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.GrpcServiceException
import org.apache.pekko.stream.Materializer
import example.urlshortner.user.grpc._
import io.grpc.Status
import repositories.UserRepo
import javax.inject.Inject
import scala.concurrent.{ExecutionContextExecutor, Future}
import java.sql.Timestamp
import java.time.Instant
import com.google.protobuf.{Timestamp => ProtoTimestamp}
import security.JwtUtility

class UserRouter @Inject() (
    mat: Materializer,
    system: ActorSystem,
    val userRepo: UserRepo,
    val jwtUtility: JwtUtility
) extends AbstractUserServiceRouter(system) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override def createUser(in: CreateUserRequest): Future[User] = {
    if (in.username.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("Username is required")
        )
      )
    } else if (in.email.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("Email is required")
        )
      )
    } else if (in.password.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("Password is required")
        )
      )
    } else {
      val newUser = models.User(
        id = 0L,
        username = in.username,
        email = in.email,
        password = Some(in.password),
        role = "USER",
        google_id = None,
        auth_provider = "LOCAL",
        is_deleted = false,
        created_at = Timestamp.from(Instant.now()),
        updated_at = Timestamp.from(Instant.now())
      )

      (for {
        generatedId <- userRepo.addUser(newUser)
      } yield {
        User(
          id = generatedId,
          username = newUser.username,
          email = newUser.email,
          password = newUser.password,
          role = newUser.role,
          googleId = None,
          authProvider = AuthProvider.LOCAL,
          isDeleted = newUser.is_deleted,
          createdAt = newUser.created_at.getTime(),
          updatedAt = newUser.updated_at.getTime()
        )
      }).recover {
        case e: java.sql.SQLException =>
          if (e.getErrorCode == 1062) {
            throw new GrpcServiceException(status = Status.ALREADY_EXISTS)
          } else {
            throw new GrpcServiceException(
              status = Status.INTERNAL.withDescription(s"Database errror: ${e.getMessage}")
            )
          }
        case e: Exception =>
          throw new GrpcServiceException(
            status = Status.INTERNAL.withDescription(s"Unknown errror: ${e.getMessage}")
          )
      }
    }
  }

  def userLogin(
      in: LoginRequest
  ): Future[LoginResponse] = {
    if (in.email.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("Email is required")
        )
      )
    } else if (in.password.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("Password is required")
        )
      )
    } else {
      for {
        userOpt <- userRepo.authenticate(in.email, in.password)
        user <- userOpt match {
          case Some(user) => Future.successful(user)
          case None =>
            Future.failed(
              new GrpcServiceException(
                status = Status.PERMISSION_DENIED.withDescription("Invalid Credentials")
              )
            )
        }
      } yield {
        val jwtToken = jwtUtility.createToken(user.email, user.role)
        LoginResponse(
          success = true,
          token = jwtToken,
          message = "Login was Successfull"
        )
      }
    }
  }

}
