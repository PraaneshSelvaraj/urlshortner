package repositories

import models.Notification
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import slick.jdbc.H2Profile.api._
import models.tables.NotificationsTable

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

class NotificationRepoSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:play;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        "play.evolutions.enabled" -> "false"
      )
      .build()

  private val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  private implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  private val repo = new NotificationRepo(dbConfigProvider)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  override def beforeAll(): Unit = {
    super.beforeAll()
    val db = dbConfigProvider.get.db
    db.run(NotificationsTable.notifications.schema.create).futureValue
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    val db = dbConfigProvider.get.db
    db.run(NotificationsTable.notifications.delete).futureValue
  }

  "NotificationRepo" should {

    "add a Notification" in {
      val notification = Notification(0L, "abc123", "NEWURL", "Created new url", Timestamp.from(Instant.now()))
      whenReady(repo.addNotification(notification)) { result =>
        result shouldBe 1
      }
    }

    "get all notifications" in {
      val n1 = Notification(0L, "abc123", "NEWURL", "Created new url", Timestamp.from(Instant.now()))
      val n2 = Notification(0L, "abc123", "TRESHOLD", "TRESHOLD REACHED", Timestamp.from(Instant.now()))

      val result = for {
        _ <- repo.addNotification(n1)
        _ <- repo.addNotification(n2)
        notifications <- repo.getNotifications
      } yield notifications

      whenReady(result) { notifications =>
        notifications.length shouldBe 2
        notifications.map(n => (n.short_code, n.notificationType, n.message)) should contain theSameElementsAs
          Seq(
            (n1.short_code, n1.notificationType, n1.message),
            (n2.short_code, n2.notificationType, n2.message)
          )
      }
    }
  }
}
