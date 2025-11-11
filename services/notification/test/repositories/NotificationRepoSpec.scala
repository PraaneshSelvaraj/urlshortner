package repositories

import models.Notification
import models.tables.{NotificationTypesTable, NotificationsTable, NotificationStatusesTable}
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

  private lazy val (
    newUrlTypeId: Int,
    thresholdTypeId: Int,
    successStatusId: Int,
    pendingStatusId: Int,
    failureStatusId: Int
  ) = {
    val setup = for {
      _ <- NotificationTypesTable.notificationTypes.schema.create
      _ <- NotificationsTable.notifications.schema.create
      _ <- NotificationTypesTable.notificationTypes += models.NotificationType(0, "NEWURL")
      _ <- NotificationTypesTable.notificationTypes += models.NotificationType(0, "TRESHOLD")
      _ <- NotificationStatusesTable.notificationStatuses.schema.create
      _ <- NotificationStatusesTable.notificationStatuses += models.NotificationStatus(0, "SUCCESS")
      _ <- NotificationStatusesTable.notificationStatuses += models.NotificationStatus(0, "PENDING")
      _ <- NotificationStatusesTable.notificationStatuses += models.NotificationStatus(0, "FAILURE")

      newUrlId <- NotificationTypesTable.notificationTypes
        .filter(_.name === "NEWURL")
        .map(_.id)
        .result
        .head
      thresholdId <- NotificationTypesTable.notificationTypes
        .filter(_.name === "TRESHOLD")
        .map(_.id)
        .result
        .head
      successId <- NotificationStatusesTable.notificationStatuses
        .filter(_.name === "SUCCESS")
        .map(_.id)
        .result
        .head
      pendingId <- NotificationStatusesTable.notificationStatuses
        .filter(_.name === "PENDING")
        .map(_.id)
        .result
        .head
      failureId <- NotificationStatusesTable.notificationStatuses
        .filter(_.name === "FAILURE")
        .map(_.id)
        .result
        .head
    } yield (newUrlId, thresholdId, successId, pendingId, failureId)

    db.run(setup.transactionally).futureValue
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    (newUrlTypeId, thresholdTypeId, successStatusId, pendingStatusId, failureStatusId)
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
      val notification = Notification(
        0L,
        Some("abc123"),
        None,
        newUrlTypeId,
        successStatusId,
        "Created new url",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      whenReady(repo.addNotification(notification)) { result =>
        result shouldBe 1
      }
    }

    "get all notifications" in {
      val n1 = Notification(
        0L,
        Some("abc123"),
        None,
        newUrlTypeId,
        successStatusId,
        "Created new url",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      val n2 = Notification(
        0L,
        Some("def456"),
        None,
        newUrlTypeId,
        pendingStatusId,
        "Created new url",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )
      val n3 = Notification(
        0L,
        Some("def456"),
        None,
        thresholdTypeId,
        failureStatusId,
        "TRESHOLD REACHED",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now())
      )

      val result = for {
        _ <- repo.addNotification(n1)
        _ <- repo.addNotification(n2)
        _ <- repo.addNotification(n3)
        notifications <- repo.getNotifications()
      } yield notifications

      whenReady(result) { notifications =>
        notifications.length shouldBe 3
        notifications.map(n =>
          (n.short_code, n.notificationType, n.notificationStatus, n.message)
        ) should contain theSameElementsAs
          Seq(
            (n1.short_code, "NEWURL", "SUCCESS", n1.message),
            (n2.short_code, "NEWURL", "PENDING", n2.message),
            (n3.short_code, "TRESHOLD", "FAILURE", n3.message)
          )
      }
    }
  }
}
