package routers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.grpc.GrpcServiceException
import org.apache.pekko.stream.Materializer
import example.urlshortner.user.grpc._
import io.grpc.Status
import repositories.UserRepo
import services.GoogleAuthService
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
    val jwtUtility: JwtUtility,
    val googleAuthService: GoogleAuthService
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

  override def userLogin(in: LoginRequest): Future[LoginResponse] = {
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
      userRepo.authenticate(in.email, in.password).flatMap {
        case Some(user) =>
          // Check if user is deleted
          if (user.is_deleted) {
            Future.failed(
              new GrpcServiceException(
                status = Status.PERMISSION_DENIED
                  .withDescription("Account has been deactivated")
              )
            )
          }
          // Check if user is using LOCAL authentication
          else if (user.auth_provider != "LOCAL") {
            Future.failed(
              new GrpcServiceException(
                status = Status.PERMISSION_DENIED
                  .withDescription(
                    s"This account uses ${user.auth_provider} authentication. Please use the appropriate login method."
                  )
              )
            )
          }
          // Valid user - return JWT token
          else {
            val jwtToken = jwtUtility.createToken(user.email, user.role)
            Future.successful(
              LoginResponse(
                success = true,
                token = jwtToken,
                message = "Login was Successfull",
                user = Some(mapToProtoUser(user))
              )
            )
          }

        case None =>
          Future.failed(
            new GrpcServiceException(
              status = Status.PERMISSION_DENIED.withDescription("Invalid Credentials")
            )
          )
      }
    }
  }

  override def googleLogin(in: GoogleLoginRequest): Future[LoginResponse] = {
    if (in.idToken.isEmpty) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("ID token is required")
        )
      )
    } else {
      googleAuthService
        .verifyToken(in.idToken)
        .flatMap {
          case Some(googleUserInfo) =>
            userRepo.findUserByEmail(googleUserInfo.email).flatMap {
              case Some(existingUser) =>
                // Check if deleted first
                if (existingUser.is_deleted) {
                  Future.failed(
                    new GrpcServiceException(
                      status = Status.PERMISSION_DENIED
                        .withDescription("Account has been deactivated")
                    )
                  )
                }
                // Check if different provider
                else if (existingUser.auth_provider != "GOOGLE") {
                  Future.failed(
                    new GrpcServiceException(
                      status = Status.ALREADY_EXISTS
                        .withDescription(
                          s"Account exists with ${existingUser.auth_provider} authentication. Please use the appropriate login method."
                        )
                    )
                  )
                }
                // Existing active Google user
                else {
                  val jwtToken = jwtUtility.createToken(existingUser.email, existingUser.role)
                  Future.successful(
                    LoginResponse(
                      success = true,
                      token = jwtToken,
                      message = "Login successful",
                      user = Some(mapToProtoUser(existingUser))
                    )
                  )
                }

              case None =>
                // New user - create account
                val newUser = models.User(
                  id = 0L,
                  username = googleUserInfo.name,
                  email = googleUserInfo.email,
                  password = None,
                  role = "USER",
                  google_id = Some(googleUserInfo.googleId),
                  auth_provider = "GOOGLE",
                  is_deleted = false,
                  created_at = Timestamp.from(Instant.now()),
                  updated_at = Timestamp.from(Instant.now())
                )

                userRepo
                  .addUser(newUser)
                  .map { userId =>
                    val jwtToken = jwtUtility.createToken(newUser.email, newUser.role)
                    LoginResponse(
                      success = true,
                      token = jwtToken,
                      message = "Account created and login successful",
                      user = Some(mapToProtoUser(newUser.copy(id = userId)))
                    )
                  }
                  .recover {
                    case e: java.sql.SQLException if e.getErrorCode == 1062 =>
                      throw new GrpcServiceException(
                        status = Status.ALREADY_EXISTS.withDescription("User already exists")
                      )
                    case e: Exception =>
                      throw new GrpcServiceException(
                        status =
                          Status.INTERNAL.withDescription(s"Failed to create user: ${e.getMessage}")
                      )
                  }
            }

          case None =>
            Future.failed(
              new GrpcServiceException(
                status = Status.UNAUTHENTICATED.withDescription("Invalid Google ID token")
              )
            )
        }
        .recover {
          case e: GrpcServiceException => throw e
          case e: Exception =>
            throw new GrpcServiceException(
              status = Status.INTERNAL.withDescription(s"Authentication failed: ${e.getMessage}")
            )
        }
    }
  }

  private def mapToProtoUser(user: models.User): User = {
    User(
      id = user.id,
      username = user.username,
      email = user.email,
      password = None, // Never send password back
      role = user.role,
      googleId = user.google_id,
      authProvider =
        if (user.auth_provider == "GOOGLE") AuthProvider.GOOGLE else AuthProvider.LOCAL,
      isDeleted = user.is_deleted,
      createdAt = user.created_at.getTime,
      updatedAt = user.updated_at.getTime
    )
  }
}
