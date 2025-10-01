package services
import dtos.CreateUserDTO
import play.api.Configuration
import example.urlshortner.user.grpc._
import models.{User => UserModel}
import java.sql.Timestamp
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class UserService @Inject() (
    userServiceClient: UserServiceClient,
    config: Configuration
)(implicit ec: ExecutionContext) {

  def addUser(userDTO: CreateUserDTO): Future[UserModel] = {

    val createUser = CreateUserRequest(
      username = userDTO.username,
      email = userDTO.email,
      password = userDTO.password
    )

    userServiceClient.createUser(createUser) map { reply =>
      UserModel(
        id = reply.id,
        username = reply.username,
        email = reply.email,
        password = reply.password,
        is_deleted = reply.isDeleted,
        created_at = new Timestamp(reply.createdAt),
        updated_at = new Timestamp(reply.updatedAt)
      )
    }

  }
}
