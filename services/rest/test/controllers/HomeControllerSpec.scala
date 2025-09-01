package controllers

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._

class HomeControllerSpec extends PlaySpec {

  "HomeController" should {
    "return ok" in {
      val controller = new HomeController(stubControllerComponents())
      val request = FakeRequest(GET, "/health")
      val result = controller.health()(request)

      status(result) mustBe OK
      contentAsString(result) mustBe "Rest Service is running"
    }
  }

}
