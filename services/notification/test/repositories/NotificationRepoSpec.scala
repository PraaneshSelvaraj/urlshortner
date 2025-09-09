package repositories

import models.Notification
import models.tables.{NotificationTypesTable, NotificationsTable}
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

  private val db = dbConfigProvider.get.db

  private lazy val (newUrlTypeId: Int, thresholdTypeId: Int) = {
    val setup = for {
      _ <- NotificationTypesTable.notificationTypes.schema.create
      _ <- NotificationsTable.notifications.schema.create
      _ <- NotificationTypesTable.notificationTypes += models.NotificationType(0, "NEWURL")
      _ <- NotificationTypesTable.notificationTypes += models.NotificationType(0, "TRESHOLD")
      newUrlId <- NotificationTypesTable.notificationTypes.filter(_.name === "NEWURL").map(_.id).result.head
      thresholdId <- NotificationTypesTable.notificationTypes.filter(_.name === "TRESHOLD").map(_.id).result.head
    } yield (newUrlId, thresholdId)

    db.run(setup.transactionally).futureValue
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    (newUrlTypeId, thresholdTypeId)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    val clearDb = for {
      _ <- NotificationsTable.notifications.delete
    } yield ()

    db.run(clearDb).futureValue
  }

  "NotificationRepo" should {

    "add a Notification" in {
      val notification = Notification(0L, "abc123", newUrlTypeId, "Created new url", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
      whenReady(repo.addNotification(notification)) { result =>
        result shouldBe 1
      }
    }

    "get all notifications" in {
      val n1 = Notification(0L, "abc123", newUrlTypeId, "Created new url", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
      val n2 = Notification(0L, "def456", thresholdTypeId, "TRESHOLD REACHED", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))

      val result = for {
        _ <- repo.addNotification(n1)
        _ <- repo.addNotification(n2)
        notifications <- repo.getNotificationsWithTypeName // Use the new method
      } yield notifications

      whenReady(result) { notifications =>
        notifications.length shouldBe 2
        notifications.map(n => (n.short_code, n.notificationType, n.message)) should contain theSameElementsAs
          Seq(
            (n1.short_code, "NEWURL", n1.message),
            (n2.short_code, "TRESHOLD", n2.message)
          )
      }
    }
  }
}
