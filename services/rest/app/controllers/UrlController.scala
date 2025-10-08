package controllers

import actions.RateLimiterAction
import dtos.UrlDto
import exceptions.TresholdReachedException
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import services.UrlService
import exceptions.UrlExpiredException
import auth.{AuthenticatedAction, AuthenticatedRequest}

class UrlController @Inject() (
    val controllerComponents: ControllerComponents,
    urlService: UrlService,
    rateLimiter: RateLimiterAction,
    authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def addUrl(): Action[AnyContent] = authenticatedAction.async {
    implicit request: AuthenticatedRequest[AnyContent] =>
      val userId = request.user.id

      request.body.asJson match {
        case Some(jsonData) =>
          jsonData.validate[UrlDto].asOpt match {
            case Some(urlData) =>
              urlService
                .addUrl(urlData, userId)
                .map { urlAdded =>
                  Created(Json.obj(("message", "Url Created successfully"), ("data", urlAdded)))
                }
                .recover { case ex: Exception =>
                  println(s"Error creating URL: ${ex.getMessage}")
                  InternalServerError(Json.obj(("message", "Failed to create URL")))
                }
            case None =>
              Future.successful(BadRequest(Json.obj(("message", "Invalid Request Body Schema"))))
          }
        case None =>
          Future.successful(BadRequest(Json.obj(("message", "Request Body needs to be JSON"))))
      }
  }

  def redirectUrl(shortCode: String): Action[AnyContent] = rateLimiter.async { implicit request =>
    urlService
      .redirect(shortCode)
      .map { url =>
        Redirect(url.long_url, TEMPORARY_REDIRECT)
      }
      .recover {
        case _: NoSuchElementException =>
          NotFound(
            Json.obj("success" -> false, "message" -> s"URL with short code '$shortCode' not found")
          )
        case _: TresholdReachedException =>
          Forbidden(
            Json.obj(
              ("success", false),
              "message" -> s"Treshold reached for the url with short code $shortCode"
            )
          )
        case _: UrlExpiredException =>
          Forbidden(
            Json.obj(
              ("success", false),
              "message" -> s"Url Expired for the url with short code $shortCode"
            )
          )
        case ex: Exception =>
          println(s"Error redirecting URL: ${ex.getMessage}")
          InternalServerError(
            Json.obj("success" -> false, "message" -> "Error processing redirect")
          )
      }
  }

  def getUrls: Action[AnyContent] = authenticatedAction.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      urlService.getAllUrls.map(urls => Ok(Json.obj(("message", "List of Urls"), ("urls", urls))))
  }

  def getUrlByShortCode(shortCode: String): Action[AnyContent] = authenticatedAction.async {
    implicit req: Request[AnyContent] =>
      urlService.getUrlByShortCode(shortCode) map {
        case Some(url) => Ok(Json.obj(("message", s"Url with shortcode $shortCode"), ("data", url)))
        case None =>
          NotFound(Json.obj(("message", s"Unable to find Url with shortcode $shortCode")))
      }
  }

  def deleteUrlByShortCode(shortCode: String): Action[AnyContent] = authenticatedAction.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      urlService
        .deleteUrlByShortCode(shortCode)
        .map(rowsAffected =>
          if (rowsAffected <= 0)
            NotFound(Json.obj(("message", s"Unable to find Url with shortCode $shortCode")))
          else NoContent
        )
        .recover {
          case _: NoSuchElementException =>
            NotFound(Json.obj(("message", s"Unable to find Url with shortCode $shortCode")))
          case ex: Exception =>
            println(s"Error Deleting URL: ${ex.getMessage}")
            InternalServerError(
              Json.obj("message" -> "Error processing redirect", "error" -> ex.getMessage)
            )
        }
  }

  def getNotifications: Action[AnyContent] = authenticatedAction.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      urlService.getNotifications map { notifications =>
        Ok(Json.obj(("message", "List of all Notifications"), ("notifications", notifications)))
      }
  }

}
