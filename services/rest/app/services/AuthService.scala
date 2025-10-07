package services

import dtos.LoginDTO
import example.urlshortner.user.grpc.{UserServiceClient, LoginRequest, LoginResponse}

import com.google.protobuf.empty.Empty
import exceptions.{TresholdReachedException, UrlExpiredException}
import java.time.{Instant, Duration}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class AuthService @Inject() (
    userServiceClient: UserServiceClient
)(implicit ec: ExecutionContext) {

  def login(email: String, password: String): Future[String] = {
    val loginRequest = LoginRequest(email, password)
    for {
      reply <- userServiceClient.userLogin(loginRequest)
      token <-
        if (reply.success) {
          Future.successful(reply.token)
        } else {
          Future.failed(new Exception(reply.message))
        }
    } yield token
  }
}
