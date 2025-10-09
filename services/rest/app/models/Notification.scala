package models

import play.api.libs.json.{Json, OFormat}

case class Notification(
    id: Long,
    short_code: Option[String],
    user_id: Option[Long],
    notificationType: String,
    notificationStatus: String,
    message: String
)

object Notification {
  implicit val notificationFormat: OFormat[Notification] = Json.format[Notification]
}
