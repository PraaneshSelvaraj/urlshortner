package services

import dtos.UrlDto
import play.api.Configuration
import models.{Notification, Url}
import repositories.UrlRepo
import example.urlshortner.notification.grpc.{GetNotificationsResponse, NotificationRequest, NotificationServiceClient, NotificationType}
import com.google.protobuf.empty.Empty

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class UrlService @Inject()(val urlRepo: UrlRepo, val notificationServiceClient: NotificationServiceClient, config: Configuration)(implicit ec: ExecutionContext) {

  def addUrl(urlData: UrlDto): Future[Url] = {
    for {
      code <- generateShortCode()
      urlAdded <- {
        val newUrl = Url(
          id = 0L,
          short_code = code,
          long_url = urlData.url,
          clicks = 0,
          created_at = new java.sql.Timestamp(System.currentTimeMillis())
        )
        urlRepo.addUrl(newUrl)
      }
      _ <- {
        val notification = NotificationRequest(
          notificationType = NotificationType.NEWURL,
          message = s"URL Created for ${urlAdded.long_url}",
          shortCode = urlAdded.short_code
        )
        val reply = notificationServiceClient.notifyMethod(notification)
        reply.map(r => println(s"Notification Status: ${r.success}, Notification Message: ${r.message}"))
      }
    } yield urlAdded
  }


  def redirect(shortCode: String): Future[Url] = {
    for {
      urlOpt <- urlRepo.getUrlByShortcode(shortCode)
      url <- urlOpt match {
        case Some(url) => Future.successful(url)
        case None => Future.failed(new NoSuchElementException(s"Url with shortcode $shortCode does not exist"))
      }
      count <- urlRepo.incrementUrlCount(shortCode)
      _ <- {
        if(count > config.get[Int]("notification.treshold")){
          val notification = NotificationRequest(notificationType = NotificationType.TRESHOLD, message = s"URL Crossed the Threshold for ${url.long_url}", shortCode = shortCode)
          val reply = notificationServiceClient.notifyMethod(notification)
          reply.map(r => println(s"Notification Status: ${r.success}, Notification Message: ${r.message}"))
        }
        else {
          Future.successful(())
        }
      }
    } yield url
  }

  def getAllUrls: Future[Seq[Url]] = urlRepo.getAllUrls

  def getNotifications: Future[Seq[Notification]] = {
    notificationServiceClient.getNotifications(Empty()) map {
      response: GetNotificationsResponse =>
        response.notifications map {
          notification =>
            Notification(
              id = notification.id,
              short_code = notification.shortCode,
              notificationType = notification.notificationType.toString(),
              message = notification.message
            )
        }
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