package controllers

import actions.RateLimiterAction
import dtos.UrlDto
import exceptions.{TresholdReachedException, InvalidUrlException, UrlExpiredException}
import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import services.UrlService
import auth.{AuthenticatedAction, AuthenticatedRequest}

class UrlController @Inject() (
    val controllerComponents: ControllerComponents,
    urlService: UrlService,
    rateLimiter: RateLimiterAction,
    authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def addUrl(): Action[AnyContent] = authenticatedAction.forUserOrAdmin.async {
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
                .recover {
                  case ex: InvalidUrlException =>
                    Forbidden(
                      Json.obj(("error", ex.getMessage))
                    )
                  case ex: Exception =>
                    println(s"Error creating URL: ${ex.getMessage}")
                    InternalServerError(Json.obj(("error", "Failed to create URL")))
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
            Json.obj("success" -> false, "error" -> s"URL with short code '$shortCode' not found")
          )
        case _: TresholdReachedException =>
          Forbidden(
            Json.obj(
              ("success", false),
              ("error", s"Treshold reached for the url with short code $shortCode")
            )
          )
        case _: UrlExpiredException =>
          Forbidden(
            Json.obj(
              ("success", false),
              ("error", s"Url Expired for the url with short code $shortCode")
            )
          )
        case ex: Exception =>
          println(s"Error redirecting URL: ${ex.getMessage}")
          InternalServerError(
            Json.obj("success" -> false, "error" -> "Error processing redirect")
          )
      }
  }

  def getUrls: Action[AnyContent] = authenticatedAction.forAdmin.async {
    implicit req: AuthenticatedRequest[AnyContent] =>
      urlService.getAllUrls.map(urls => Ok(Json.obj(("message", "List of Urls"), ("urls", urls))))
  }

  def getUrlByShortCode(shortCode: String): Action[AnyContent] =
    authenticatedAction.forUserOrAdmin.async { implicit req: Request[AnyContent] =>
      urlService.getUrlByShortCode(shortCode) map {
        case Some(url) => Ok(Json.obj(("message", s"Url with shortcode $shortCode"), ("data", url)))
        case None =>
          NotFound(Json.obj(("message", s"Unable to find Url with shortcode $shortCode")))
      }
    }

  def deleteUrlByShortCode(shortCode: String): Action[AnyContent] =
    authenticatedAction.forUserOrAdmin.async { implicit req: AuthenticatedRequest[AnyContent] =>
      (for {
        urlOption <- urlService.getUrlByShortCode(shortCode)

        result <- urlOption match {
          case Some(url) =>
            if (url.user_id != req.user.id && req.user.role != "ADMIN") {
              Future.successful(
                Forbidden(Json.obj("message" -> "You can only delete your own URLs"))
              )
            } else {
              urlService
                .deleteUrlByShortCode(shortCode)
                .map { rowsAffected =>
                  if (rowsAffected > 0) NoContent
                  else
                    NotFound(
                      Json.obj("message" -> s"Unable to delete URL with shortCode $shortCode")
                    )
                }
                .recover { case ex: Exception =>
                  println(s"Error Deleting URL: ${ex.getMessage}")
                  InternalServerError(
                    Json.obj("message" -> "Error deleting URL", "error" -> ex.getMessage)
                  )
                }
            }
          case None =>
            Future.successful(
              NotFound(Json.obj("message" -> s"Unable to find URL with shortCode $shortCode"))
            )
        }
      } yield result).recover { case ex: Exception =>
        println(s"Error fetching URL: ${ex.getMessage}")
        InternalServerError(
          Json.obj("message" -> "Error processing request", "error" -> ex.getMessage)
        )
      }
    }

  def getNotifications: Action[AnyContent] = authenticatedAction.forAdmin.async {
    implicit req: Request[AnyContent] =>
      urlService.getNotifications map { notifications =>
        Ok(Json.obj(("message", "List of all Notifications"), ("notifications", notifications)))
      }
  }

}
