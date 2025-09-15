package controllers

import jakarta.inject._
import play.api.mvc._

class HomeController @Inject() (val controllerComponents: ControllerComponents)
    extends BaseController {

  def health: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok("Rest Service is running")
  }

}
