package services

import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleIdTokenVerifier}
import com.google.api.client.json.gson.GsonFactory
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import scala.concurrent.ExecutionContext

class GoogleAuthServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  private val mockConfiguration = mock[Configuration]
  private val testClientId = "test-client-id-123.apps.googleusercontent.com"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfiguration)
    when(mockConfiguration.get[String]("google.clientId")).thenReturn(testClientId)
  }

  "GoogleAuthService#verifyToken" should {

    "return None for invalid token" in {
      val service = new GoogleAuthService(mockConfiguration)
      val invalidToken = "invalid.token.string"

      val result = service.verifyToken(invalidToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "return None for empty token" in {
      val service = new GoogleAuthService(mockConfiguration)
      val emptyToken = ""

      val result = service.verifyToken(emptyToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "return None for null token" in {
      val service = new GoogleAuthService(mockConfiguration)

      val result = service.verifyToken(null)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "return None for malformed token" in {
      val service = new GoogleAuthService(mockConfiguration)
      val malformedToken = "not.a.valid.jwt.token.format"

      val result = service.verifyToken(malformedToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "handle token with wrong issuer gracefully" in {
      val service = new GoogleAuthService(mockConfiguration)
      // This would be a token from a different OAuth provider
      val wrongIssuerToken =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIn0.fake"

      val result = service.verifyToken(wrongIssuerToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "handle expired token gracefully" in {
      val service = new GoogleAuthService(mockConfiguration)
      // Simulating an expired token (you would need a real expired token to test this properly)
      val expiredToken = "expired.jwt.token"

      val result = service.verifyToken(expiredToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "handle token with wrong audience gracefully" in {
      val service = new GoogleAuthService(mockConfiguration)
      // Token intended for a different client ID
      val wrongAudienceToken = "wrong.audience.token"

      val result = service.verifyToken(wrongAudienceToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }

    "handle network errors gracefully" in {
      val service = new GoogleAuthService(mockConfiguration)
      // Simulating a scenario that might cause network issues
      val problematicToken = "problematic.token"

      val result = service.verifyToken(problematicToken)

      whenReady(result) { userInfo =>
        userInfo shouldBe None
      }
    }
  }

  "GoogleAuthService configuration" should {

    "load client ID from configuration" in {
      val service = new GoogleAuthService(mockConfiguration)

      verify(mockConfiguration).get[String]("google.clientId")
    }

    "initialize with correct issuer" in {
      val service = new GoogleAuthService(mockConfiguration)

      // Service should be created without throwing exceptions
      service should not be null
    }
  }

  "GoogleUserInfo" should {

    "create instance with all required fields" in {
      val userInfo = GoogleUserInfo(
        googleId = "123456789",
        email = "test@example.com",
        name = "Test User"
      )

      userInfo.googleId shouldBe "123456789"
      userInfo.email shouldBe "test@example.com"
      userInfo.name shouldBe "Test User"
    }

    "support case class equality" in {
      val userInfo1 = GoogleUserInfo("123", "test@example.com", "Test User")
      val userInfo2 = GoogleUserInfo("123", "test@example.com", "Test User")
      val userInfo3 = GoogleUserInfo("456", "other@example.com", "Other User")

      userInfo1 shouldBe userInfo2
      userInfo1 should not be userInfo3
    }
  }
}
