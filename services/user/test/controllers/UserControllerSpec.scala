package controllers

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._

class UserControllerSpec extends PlaySpec {

  val controller = new UserController(stubControllerComponents())

  "UserController" should {

    "return 200 OK with success message" in {
      val request = FakeRequest(GET, "/health")
      val result = controller.health()(request)

      status(result) mustBe OK
      contentAsString(result) mustBe "User service is running....."
    }
  }
}
