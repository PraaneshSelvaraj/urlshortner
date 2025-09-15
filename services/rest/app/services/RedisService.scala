package services

import io.lettuce.core.{RedisClient, RedisURI, Range}
import javax.inject._
import play.api.Configuration
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import scala.jdk.FutureConverters._

@Singleton
class RedisService @Inject() (config: Configuration)(implicit ec: ExecutionContext) {

  private val host = config.get[String]("redis.host")
  private val port = config.get[Int]("redis.port")
  private val redisUri = RedisURI.Builder.redis(host, port).build()
  private val client = RedisClient.create(redisUri)
  private val connection = client.connect()
  private val redis = connection.async()

  private val excludedPaths = Set("favicon.ico", "robots.txt", "sitemap.xml")

  def isAllowed(shortCode: String, limit: Int, windowSeconds: Int): Future[Boolean] = {
    if (excludedPaths.contains(shortCode.toLowerCase)) return Future.successful(true)

    val now = Instant.now().getEpochSecond
    val windowStart = now - windowSeconds
    val key = s"req:$shortCode"
    val member = now.toString

    for {
      _ <- redis
        .zremrangebyscore(key, Range.create(Double.NegativeInfinity, windowStart.toDouble))
        .asScala
      currentCount <- redis
        .zcount(key, Range.create(windowStart.toDouble, Double.PositiveInfinity))
        .asScala
      allowed = currentCount < limit
      _ <-
        if (allowed) for {
          _ <- redis.zadd(key, now.toDouble, member).asScala
          _ <- redis.expire(key, windowSeconds * 3).asScala
        } yield ()
        else Future.successful(())
    } yield allowed
  }

  def close(): Unit = {
    connection.close()
    client.shutdown()
  }
}
