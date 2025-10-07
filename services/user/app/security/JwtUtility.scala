package security

import pdi.jwt._
import pdi.jwt.algorithms._
import play.api.Configuration
import play.api.libs.json.Json

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.util.Try

@Singleton
class JwtUtility @Inject() (configuration: Configuration) {

  private val secretKey = configuration.get[String]("jwt.secretKey")

  private val expirationSeconds = configuration.get[Int]("jwt.expirationSecond")

  private val algorithm =
    JwtAlgorithm.fromString(configuration.get[String]("jwt.algorithm")) match {
      case algo: JwtHmacAlgorithm => algo
      case _                      => throw new IllegalArgumentException("Invalid JWT Algorithm")
    }

  private implicit val clock: Clock = Clock.systemUTC

  def createToken(email: String, role: String): String = {
    val claim = JwtClaim(
      content = Json
        .obj(
          "email" -> email,
          "role" -> role
        )
        .toString()
    ).issuedNow
      .expiresIn(expirationSeconds)

    JwtJson.encode(claim, secretKey, algorithm)
  }

  def decodeToken(token: String): Try[JwtClaim] = {
    JwtJson.decode(token, secretKey, Seq(algorithm))
  }

  def getClaimsData(claim: JwtClaim): Option[(String, String)] = Try {
    val jsonContent = Json.parse(claim.content)
    val email = (jsonContent \ "email").as[String]
    val role = (jsonContent \ "role").as[String]
    (email, role)
  }.toOption

}
