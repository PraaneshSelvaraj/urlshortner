package models

import play.api.libs.json.{Json, OFormat, Reads, Writes}
import java.sql.Timestamp

case class Notification(
    id: Long,
    short_code: Option[String],
    user_id: Option[Long],
    notification_type_id: Int,
    notification_status_id: Int,
    message: String,
    created_at: Timestamp,
    updated_at: Timestamp
)

object Notification {
  implicit val timestampReads: Reads[Timestamp] = Reads.of[Long].map(new Timestamp(_))
  implicit val timestampWrites: Writes[Timestamp] = Writes.of[Long].contramap(_.getTime)

  implicit val notificationFormat: OFormat[Notification] = Json.format[Notification]
}
