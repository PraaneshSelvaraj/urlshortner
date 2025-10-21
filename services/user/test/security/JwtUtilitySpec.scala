package security

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import pdi.jwt.{JwtJson, JwtAlgorithm}
import play.api.libs.json.Json

class JwtUtilitySpec extends AnyFlatSpec with Matchers {

  val config: Configuration = Configuration.from(
    Map(
      "jwt.secretKey" -> "my-secret-key",
      "jwt.expirationSecond" -> 3600,
      "jwt.algorithm" -> "HS256"
    )
  )

  val jwtUtility = new JwtUtility(config)

  "createToken" should "return a valid JWT string" in {
    val email = "test@example.com"
    val role = "user"

    val token = jwtUtility.createToken(email, role)
    token should not be empty

    val decoded = JwtJson.decodeJson(token, "my-secret-key", Seq(JwtAlgorithm.HS256))
    decoded.isSuccess shouldBe true

    val json = decoded.get
    (json \ "email").as[String] shouldBe email
    (json \ "role").as[String] shouldBe role
  }

  it should "expire in the configured time" in {
    val email = "expire@test.com"
    val role = "admin"

    val token = jwtUtility.createToken(email, role)
    val decodedClaim = JwtJson.decode(token, "my-secret-key", Seq(JwtAlgorithm.HS256))
    decodedClaim.isSuccess shouldBe true

    val claim = decodedClaim.get
    val expiration = claim.expiration.get
    val issuedAt = claim.issuedAt.get

    expiration - issuedAt shouldBe 3600
  }

  it should "throw exception for invalid algorithm" in {
    val invalidConfig: Configuration = Configuration.from(
      Map(
        "jwt.secretKey" -> "secret",
        "jwt.expirationSecond" -> 3600,
        "jwt.algorithm" -> "RS256"
      )
    )

    assertThrows[IllegalArgumentException] {
      new JwtUtility(invalidConfig)
    }
  }
}
