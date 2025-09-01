package controllers

import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class NotificationController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {

  def health: Action[AnyContent] = Action {implicit req: Request[AnyContent] =>
    Ok("Notification service is running.....")
  }

}
