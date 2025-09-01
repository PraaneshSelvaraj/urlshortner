package controllers

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._

class NotificationControllerSpec extends PlaySpec {

  "HomeController" should {
    "return ok" in {
      val controller = new NotificationController(stubControllerComponents())
      val request = FakeRequest(GET, "/health")
      val result = controller.health()(request)

      status(result) mustBe OK
      contentAsString(result) mustBe "Notification service is running....."
    }
  }

}
