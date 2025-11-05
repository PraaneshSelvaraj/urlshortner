package security

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import pdi.jwt.{JwtJson, JwtClaim, JwtAlgorithm}
import play.api.libs.json.Json
import scala.util.Try

class JwtUtilitySpec extends AnyFlatSpec with Matchers {

  val config: Configuration = Configuration.from(
    Map(
      "jwt.secretKey" -> "my-secret-key",
      "jwt.expirationSeconds" -> 3600,
      "jwt.algorithm" -> "HS256"
    )
  )

  val jwtUtility = new JwtUtility(config)

  "decodeToken" should "successfully decode a valid token" in {
    val claim = JwtClaim(
      content = Json
        .obj(
          "email" -> "test@example.com",
          "role" -> "user"
        )
        .toString()
    )
    val token = JwtJson.encode(claim, "my-secret-key", JwtAlgorithm.HS256)

    val decodedClaim = jwtUtility.decodeToken(token)
    decodedClaim.isSuccess shouldBe true

    val claimsData = jwtUtility.getClaimsData(decodedClaim.get)
    claimsData shouldBe Some(("test@example.com", "user"))
  }

  it should "return Failure for invalid token" in {
    val invalidToken = "invalid.token.string"
    val decodedClaim = jwtUtility.decodeToken(invalidToken)
    decodedClaim.isFailure shouldBe true
  }

  "getClaimsData" should "return None for malformed claim content" in {
    val badClaim = JwtClaim(content = "not-a-json")
    val result = jwtUtility.getClaimsData(badClaim)
    result shouldBe None
  }

  it should "extract email and role correctly from valid claim" in {
    val claim = JwtClaim(
      content = Json
        .obj(
          "email" -> "user@example.com",
          "role" -> "admin"
        )
        .toString()
    )
    val result = jwtUtility.getClaimsData(claim)
    result shouldBe Some(("user@example.com", "admin"))
  }
}
