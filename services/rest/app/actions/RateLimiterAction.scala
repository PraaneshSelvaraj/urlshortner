package actions

import javax.inject._
import play.api.mvc._
import play.api.Configuration
import services.RedisService
import scala.concurrent.{ExecutionContext, Future}

class RateLimiterAction @Inject()(val redisService: RedisService, val configuration: Configuration, val parser: BodyParsers.Default, val controllerComponents: ControllerComponents)(implicit ec: ExecutionContext)
  extends ActionBuilder[Request, AnyContent] {

  private val limit = configuration.get[Int]("ratelimiter.limit")
  private val windowSeconds = configuration.get[Int]("ratelimiter.windowSeconds")

  override protected def executionContext: ExecutionContext = ec

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    val shortCode = request.path.split("/").last

    redisService.isAllowed(shortCode, limit, windowSeconds).flatMap {
      case true => block(request)
      case false => Future.successful(Results.TooManyRequests(s"Rate limit exceeded. Try again after $windowSeconds Seconds."))
    }
  }
}
