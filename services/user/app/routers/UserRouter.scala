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
import scala.util.Failure
import scala.util.Success

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
      val userRole = in.userRole match {
        case Some(UserRole.ADMIN) => "ADMIN"
        case _                    => "USER"
      }
      val newUser = models.User(
        id = 0L,
        username = in.username,
        email = in.email,
        password = Some(in.password),
        role = userRole,
        google_id = None,
        auth_provider = "LOCAL",
        refresh_token = None,
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
          role = newUser.role match {
            case "USER"  => UserRole.USER
            case "ADMIN" => UserRole.ADMIN
          },
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
            val accessToken = jwtUtility.createToken(user.email, user.role)
            val refreshToken = jwtUtility.createRefreshToken(user.email, user.role)

            userRepo.updateRefreshToken(user.id, refreshToken) flatMap { rowsAffected =>
              if (rowsAffected <= 0)
                Future.failed(
                  new GrpcServiceException(status =
                    Status.UNKNOWN.withDescription("Unable to login")
                  )
                )
              else
                Future.successful(
                  LoginResponse(
                    success = true,
                    isUserCreated = false,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    message = "Login was Successfull",
                    user = Some(mapToProtoUser(user))
                  )
                )
            }
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
                  val accessToken = jwtUtility.createToken(existingUser.email, existingUser.role)
                  val refreshToken =
                    jwtUtility.createRefreshToken(existingUser.email, existingUser.role)

                  userRepo.updateRefreshToken(existingUser.id, refreshToken) flatMap {
                    rowsAffected =>
                      if (rowsAffected <= 0)
                        Future.failed(
                          new GrpcServiceException(status =
                            Status.UNKNOWN.withDescription("Unable to login")
                          )
                        )
                      else
                        Future.successful(
                          LoginResponse(
                            success = true,
                            isUserCreated = false,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            message = "Login was Successfull",
                            user = Some(mapToProtoUser(existingUser))
                          )
                        )
                  }
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
                  refresh_token = None,
                  is_deleted = false,
                  created_at = Timestamp.from(Instant.now()),
                  updated_at = Timestamp.from(Instant.now())
                )

                userRepo
                  .addUser(newUser)
                  .map { userId =>
                    val accessToken = jwtUtility.createToken(newUser.email, newUser.role)
                    val refreshToken = jwtUtility.createRefreshToken(newUser.email, newUser.role)
                    LoginResponse(
                      success = true,
                      isUserCreated = true,
                      accessToken = accessToken,
                      refreshToken = refreshToken,
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

  override def getUserById(in: GetUserRequest): Future[User] = {
    if (in.id <= 0) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("ID should be valid.")
        )
      )
    } else {
      userRepo.getUserById(in.id) flatMap {
        case Some(user) =>
          if (user.is_deleted) {
            Future.failed(
              new GrpcServiceException(
                status =
                  Status.NOT_FOUND.withDescription(s"Unable to find user with Id: ${user.id}")
              )
            )
          } else {
            val authProvider = user.auth_provider match {
              case "LOCAL"  => AuthProvider.LOCAL
              case "GOOGLE" => AuthProvider.GOOGLE
              case _ =>
                throw new NoSuchElementException(
                  s"Unknown notification type: ${user.auth_provider}"
                )
            }

            val userRole = user.role match {
              case "ADMIN" => UserRole.ADMIN
              case "USER"  => UserRole.USER
              case _ =>
                throw new NoSuchElementException(
                  s"Unknown user role: ${user.role}"
                )
            }

            Future.successful(
              User(
                id = user.id,
                username = user.username,
                email = user.email,
                password = user.password,
                role = userRole,
                googleId = user.google_id,
                authProvider = authProvider,
                isDeleted = user.is_deleted,
                createdAt = user.created_at.getTime(),
                updatedAt = user.updated_at.getTime()
              )
            )

          }
        case None =>
          Future.failed(
            new GrpcServiceException(
              status = Status.NOT_FOUND.withDescription(s"Unable to find User with Id: ${in.id}")
            )
          )
      }
    }
  }

  override def deleteUserById(in: DeleteUserRequest): Future[DeleteUserResponse] = {
    if (in.id <= 0) {
      Future.failed(
        new GrpcServiceException(
          status = Status.INVALID_ARGUMENT.withDescription("ID should be valid.")
        )
      )
    } else {
      userRepo.deleteUserById(in.id) flatMap { rowsAffected =>
        if (rowsAffected == 0) {
          Future.failed(
            new GrpcServiceException(
              status = Status.UNKNOWN.withDescription(s"Unable to User with Id: ${in.id}")
            )
          )
        } else {
          Future.successful(
            DeleteUserResponse(
              id = in.id,
              success = true
            )
          )
        }
      }
    }
  }

  def refreshTokens(in: RefreshTokenRequest): Future[RefreshTokenResponse] = {
    jwtUtility.decodeRefreshToken(in.refreshToken) match {
      case Failure(_) =>
        Future.failed(
          new GrpcServiceException(Status.UNAUTHENTICATED.withDescription("Invalid refresh token"))
        )
      case Success(claim) =>
        jwtUtility.getRefreshClaimsData(claim) match {
          case Some((email, role)) =>
            userRepo.findUserByEmail(email) flatMap {
              case Some(user) =>
                userRepo.getRefreshToken(user.id) flatMap {
                  case Some(token) if (in.refreshToken == token) => {
                    val newAccessToken = jwtUtility.createToken(user.email, user.role)
                    val newRefreshToken = jwtUtility.createRefreshToken(user.email, user.role)

                    userRepo.updateRefreshToken(user.id, newRefreshToken) map { _ =>
                      RefreshTokenResponse(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken
                      )
                    }
                  }
                  case _ =>
                    Future.failed(
                      new GrpcServiceException(
                        Status.UNAUTHENTICATED.withDescription("Invalid Refresh Token")
                      )
                    )
                }
              case None =>
                Future.failed(
                  new GrpcServiceException(
                    Status.UNAUTHENTICATED.withDescription("Unable to find the user")
                  )
                )
            }
          case None =>
            Future.failed(
              new GrpcServiceException(
                Status.UNAUTHENTICATED.withDescription("Invalid refresh token claims")
              )
            )
        }
    }
  }

  private def mapToProtoUser(user: models.User): User = {
    User(
      id = user.id,
      username = user.username,
      email = user.email,
      password = None,
      role = user.role match {
        case "ADMIN" => UserRole.ADMIN
        case "USER"  => UserRole.USER
      },
      googleId = user.google_id,
      authProvider =
        if (user.auth_provider == "GOOGLE") AuthProvider.GOOGLE else AuthProvider.LOCAL,
      isDeleted = user.is_deleted,
      createdAt = user.created_at.getTime,
      updatedAt = user.updated_at.getTime
    )
  }
}
