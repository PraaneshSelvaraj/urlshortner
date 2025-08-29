package controllers

import com.google.protobuf.empty.Empty
import dtos.UrlDto
import play.api.libs.json.Json
import play.api.mvc._
import play.api.Configuration
import repositories.UrlRepo
import models.{Notification, Url}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}
import example.urlshortner.notification.grpc.{GetNotificationsResponse, NotificationRequest, NotificationServiceClient, NotificationType}

class UrlController @Inject()(val controllerComponents: ControllerComponents, val urlRepo: UrlRepo, val notificationServiceClient: NotificationServiceClient, config: Configuration)(implicit ec: ExecutionContext)  extends BaseController{

  def addUrl(): Action[AnyContent] = Action.async {implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(jsonData) => jsonData.validate[UrlDto].asOpt match {
        case Some(urlData) => {
          generateShortCode().flatMap {
            code => {
              val newUrl = Url(
                id = 0L,
                short_code = code,
                long_url = urlData.url,
                clicks = 0,
                created_at = new java.sql.Timestamp(System.currentTimeMillis())
              )

              urlRepo.addUrl(newUrl) match {
                case Success(urlFuture) => urlFuture map{
                  urlAdded => {
                    val notification = NotificationRequest(notificationType = NotificationType.NEWURL, message = s"URL Created for ${newUrl.long_url}", shortCode = newUrl.short_code)
                    val reply = notificationServiceClient.notifyMethod(notification)
                    reply.map(r => println(s"Notification Status: ${r.success}, Notification Message: ${r.message}"))
                    Created(Json.obj(("message", "URL created successfully"), ("data", urlAdded)))
                  }
                }
                case Failure(exception) => Future.successful(InternalServerError(Json.obj(("message", "Unable to add url"), ("errors", exception.toString))))
              }
            }.recover {
              case ex: Exception => InternalServerError(Json.obj(("message", "Unable to add url"), ("errors", ex.toString)))
            }
          }
        }
        case None => Future.successful(BadRequest(Json.obj(("message", "Invalid Request Body Schema"))))
      }
      case None => Future.successful(BadRequest(Json.obj(("message", "Request Body needs to be JSON"))))
    }
  }


  def redirectUrl(shortCode: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    urlRepo.getUrlByShortcode(shortCode).map {
      case Some(url) =>{
        urlRepo.incrementUrlCount(shortCode).map{
          count => if(count > config.get[Int]("notification.treshold")) {
            val notification = NotificationRequest(notificationType = NotificationType.TRESHOLD, message = s"URL Crossed the Threshold for ${url.long_url}", shortCode = shortCode)
            val reply = notificationServiceClient.notifyMethod(notification)
            reply.map(r => println(s"Notification Status: ${r.success}, Notification Message: ${r.message}"))
          }
        }
        Redirect(url.long_url, TEMPORARY_REDIRECT)
      }
      case None => NotFound(Json.obj(("message", s"$shortCode doesn't exist")))
    }
  }

  def getUrls: Action[AnyContent] = Action.async {implicit req: Request[AnyContent] =>
    urlRepo.getAllUrls.map(urls => Ok(Json.obj(("message", "List of Urls"), ("urls", urls))))
  }

  def getNotifications: Action[AnyContent] = Action.async {implicit req: Request[AnyContent] =>
    notificationServiceClient.getNotifications(Empty()).map {
      response: GetNotificationsResponse =>
        val notifications = response.notifications.map {
        notification => Notification(
          id = notification.id,
          short_code = notification.shortCode,
          notificationType = notification.notificationType.toString(),
          message = notification.message
        )
      }
        Ok(Json.obj(("message", "List of all Notifications"), ("notifications", notifications)))
    }
  }

  private def getRandomString(length: Int): String = {
    Iterator.continually(Random.nextPrintableChar())
      .filter(_.isLetterOrDigit)
      .take(length)
      .mkString
  }

  private def generateShortCode(length: Int = 7): Future[String] = {
    val code = getRandomString(length)

    urlRepo.getUrlByShortcode(code).flatMap {
      case None => Future.successful(code)
      case Some(_) => generateShortCode()
    }
  }
}
