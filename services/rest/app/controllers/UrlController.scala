package controllers

import dtos.UrlDto
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import services.UrlService

class UrlController @Inject()(val controllerComponents: ControllerComponents, val urlService: UrlService)(implicit ec: ExecutionContext)  extends BaseController{

  def addUrl(): Action[AnyContent] = Action.async {implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(jsonData) => jsonData.validate[UrlDto].asOpt match {
        case Some(urlData) => urlService.addUrl(urlData).map {
          urlAdded => Created(Json.obj(("message", "Url Created successfully"), ("data", urlAdded)))
        }.recover {
          case ex: Exception =>
            println(s"Error creating URL: ${ex.getMessage}")
            InternalServerError(Json.obj(("message", "Failed to create URL")))
        }
        case None => Future.successful(BadRequest(Json.obj(("message", "Invalid Request Body Schema"))))
      }
      case None => Future.successful(BadRequest(Json.obj(("message", "Request Body needs to be JSON"))))
    }
  }

  def redirectUrl(shortCode: String): Action[AnyContent] = Action.async { implicit request =>
    urlService.redirect(shortCode).map { url =>
      Redirect(url.long_url, TEMPORARY_REDIRECT)
    }.recover {
      case _: NoSuchElementException =>
        NotFound(Json.obj("success" -> false, "message" -> s"URL with short code '$shortCode' not found"))
      case ex: Exception =>
        println(s"Error redirecting URL: ${ex.getMessage}")
        InternalServerError(Json.obj("success" -> false, "message" -> "Error processing redirect"))
    }
  }

  def getUrls: Action[AnyContent] = Action.async {implicit req: Request[AnyContent] =>
    urlService.getAllUrls.map(urls => Ok(Json.obj(("message", "List of Urls"), ("urls", urls))))
  }

  def getNotifications: Action[AnyContent] = Action.async {implicit req: Request[AnyContent] =>
    urlService.getNotifications map {
      notifications => Ok(Json.obj(("message", "List of all Notifications"), ("notifications", notifications)))
    }
  }

}