package security

import pdi.jwt._
import pdi.jwt.algorithms._
import play.api.Configuration
import play.api.libs.json.Json

import java.time.Clock
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.util.Try

@Singleton
class JwtUtility @Inject() (configuration: Configuration) {

  private val secretKey = configuration.get[String]("jwt.secretKey")

  private val expirationSeconds = configuration.get[Int]("jwt.expirationSeconds")

  private val refreshExpirationSeconds = configuration.get[Int]("jwt.refreshExpirationSeconds")

  private val refreshSecretKey = configuration.get[String]("jwt.refreshSecretKey")

  private val algorithm =
    JwtAlgorithm.fromString(configuration.get[String]("jwt.algorithm")) match {
      case algo: JwtHmacAlgorithm => algo
      case _                      => throw new IllegalArgumentException("Invalid JWT Algorithm")
    }

  private implicit val clock: Clock = Clock.systemUTC

  def createToken(email: String, role: String): String = {
    val jti = UUID.randomUUID().toString
    val claim = JwtClaim(
      content = Json
        .obj(
          "email" -> email,
          "role" -> role,
          "jti" -> jti
        )
        .toString()
    ).issuedNow
      .expiresIn(expirationSeconds)

    JwtJson.encode(claim, secretKey, algorithm)
  }

  def createRefreshToken(email: String, role: String): String = {
    val jti = UUID.randomUUID().toString
    val claim = JwtClaim(
      content = Json
        .obj(
          "email" -> email,
          "role" -> role,
          "type" -> "refresh",
          "jti" -> jti
        )
        .toString()
    ).issuedNow.expiresIn(refreshExpirationSeconds)

    JwtJson.encode(claim, refreshSecretKey, algorithm)
  }

  def decodeRefreshToken(token: String): Try[JwtClaim] = {
    JwtJson.decode(token, refreshSecretKey, Seq(algorithm))
  }

  def getRefreshClaimsData(claim: JwtClaim): Option[(String, String, String)] = Try {
    val jsonContent = Json.parse(claim.content)
    val email = (jsonContent \ "email").as[String]
    val role = (jsonContent \ "role").as[String]
    val jti = (jsonContent \ "jti").as[String]
    val tokenType = (jsonContent \ "type").asOpt[String]

    if (tokenType.contains("refresh")) (email, role, jti)
    else throw new IllegalArgumentException("Not a refresh token")
  }.toOption

  def getClaimsData(claim: JwtClaim): Option[(String, String, String)] = Try {
    val jsonContent = Json.parse(claim.content)
    val email = (jsonContent \ "email").as[String]
    val role = (jsonContent \ "role").as[String]
    val jti = (jsonContent \ "jti").as[String]
    (email, role, jti)
  }.toOption
}
