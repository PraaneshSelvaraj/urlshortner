package services

import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleIdTokenVerifier}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.util.Collections

case class GoogleUserInfo(
    googleId: String,
    email: String,
    name: String
)

@Singleton
class GoogleAuthService @Inject() (
    configuration: Configuration
)(implicit ec: ExecutionContext) {

  private val clientId = configuration.get[String]("google.clientId")

  private val verifier = new GoogleIdTokenVerifier.Builder(
    new NetHttpTransport(),
    GsonFactory.getDefaultInstance()
  )
    .setAudience(Collections.singletonList(clientId))
    .setIssuer("https://accounts.google.com")
    .build()

  def verifyToken(idTokenString: String): Future[Option[GoogleUserInfo]] = {
    Future {
      Try {
        Option(verifier.verify(idTokenString)).map { googleIdToken =>
          val payload = googleIdToken.getPayload
          GoogleUserInfo(
            googleId = payload.getSubject,
            email = payload.getEmail,
            name = payload.get("name").toString
          )
        }
      }.toOption.flatten
    }
  }
}
