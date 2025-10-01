package controllers

import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class UserController @Inject() (val controllerComponents: ControllerComponents)
    extends BaseController {

  def health: Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok("User service is running.....")
  }

}
