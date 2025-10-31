package services

import dtos.UrlDto
import play.api.Configuration
import models.{Notification, Url}
import repositories.UrlRepo
import example.urlshortner.notification.grpc.{
  GetNotificationsResponse,
  NotificationRequest,
  NotificationServiceClient,
  NotificationType
}
import com.google.protobuf.empty.Empty
import exceptions.{TresholdReachedException, UrlExpiredException, InvalidUrlException}
import java.time.{Instant, Duration}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class UrlService @Inject() (
    urlRepo: UrlRepo,
    notificationServiceClient: NotificationServiceClient,
    config: Configuration
)(implicit ec: ExecutionContext) {

  def addUrl(urlData: UrlDto, userId: Long): Future[Url] = {
    if (!isValidUrl(urlData.url)) {
      Future.failed(new InvalidUrlException)
    } else {
      for {
        code <- generateShortCode()
        currentTime = new java.sql.Timestamp(System.currentTimeMillis())
        urlExpirationHours = config.get[Int]("urlExpirationHours")
        expiresAt = new java.sql.Timestamp(
          currentTime.toInstant.plus(Duration.ofHours(urlExpirationHours)).toEpochMilli
        )
        urlAdded <- {
          val newUrl = Url(
            id = 0L,
            user_id = userId,
            short_code = code,
            long_url = urlData.url,
            clicks = 0,
            is_deleted = false,
            created_at = currentTime,
            updated_at = currentTime,
            expires_at = expiresAt
          )
          urlRepo.addUrl(newUrl)
        }
        _ <- {
          val notification = NotificationRequest(
            notificationType = NotificationType.NEWURL,
            message = s"URL Created for ${urlAdded.long_url}",
            shortCode = Some(urlAdded.short_code)
          )
          val reply = notificationServiceClient.notifyMethod(notification)
          reply.map(r =>
            println(
              s"Notification Success: ${r.success}, Notification Status: ${r.notificationStatus}  Notification Message: ${r.message}"
            )
          )
        }
      } yield urlAdded
    }
  }

  def redirect(shortCode: String): Future[Url] = {
    for {
      urlOpt <- urlRepo.getUrlByShortcode(shortCode)
      url <- urlOpt match {
        case Some(url) => Future.successful(url)
        case None =>
          Future.failed(new NoSuchElementException(s"Url with shortcode $shortCode does not exist"))
      }
      _ <- {
        val currentTime = new java.sql.Timestamp(System.currentTimeMillis())
        if (currentTime.after(url.expires_at)) {
          Future.failed(new UrlExpiredException)
        } else {
          Future.successful(())
        }
      }
      count <- urlRepo.incrementUrlCount(shortCode)
      _ <- {
        if (count > config.get[Int]("notification.treshold")) {
          val notification = NotificationRequest(
            notificationType = NotificationType.TRESHOLD,
            message = s"URL Crossed the Threshold for ${url.long_url}",
            shortCode = Option(shortCode)
          )
          val reply = notificationServiceClient.notifyMethod(notification)
          reply.map(r =>
            println(s"Notification Status: ${r.success}, Notification Message: ${r.message}")
          )
          Future.failed(new TresholdReachedException)
        } else {
          Future.successful(())
        }
      }
    } yield url
  }

  def getAllUrls: Future[Seq[Url]] = urlRepo.getAllUrls

  def getUrlByShortCode(shortCode: String): Future[Option[Url]] =
    urlRepo.getUrlByShortcode(shortCode)

  def deleteUrlByShortCode(shortCode: String): Future[Int] = {
    for {
      urlOpt <- urlRepo.getUrlByShortcode(shortCode)
      url <- urlOpt match {
        case Some(url) => Future.successful(url)
        case None =>
          Future.failed(new NoSuchElementException(s"Unable to find Url with shortCode $shortCode"))
      }
      rowsAffected <- urlRepo.deleteUrlByShortCode(url.short_code)
    } yield rowsAffected
  }

  def getNotifications: Future[Seq[Notification]] = {
    notificationServiceClient.getNotifications(Empty()) map { response: GetNotificationsResponse =>
      response.notifications map { notification =>
        Notification(
          id = notification.id,
          short_code = notification.shortCode,
          user_id = notification.userId,
          notificationType = notification.notificationType.toString(),
          notificationStatus = notification.notificationStatus.toString(),
          message = notification.message
        )
      }
    }
  }

  def isValidUrl(url: String): Boolean = {
    val bannedHosts: Set[String] = config.get[Seq[String]]("bannedHosts").toSet
    try {
      val uri = new java.net.URI(url)
      val host = uri.getHost

      host != null && !bannedHosts.contains(host)
    } catch {
      case _: Exception => false
    }
  }

  private def getRandomString(length: Int): String = {
    Iterator
      .continually(Random.nextPrintableChar())
      .filter(_.isLetterOrDigit)
      .take(length)
      .mkString
  }

  private def generateShortCode(length: Int = 7): Future[String] = {
    val code = getRandomString(length)
    urlRepo.getUrlByShortcode(code).flatMap {
      case None    => Future.successful(code)
      case Some(_) => generateShortCode()
    }
  }
}
