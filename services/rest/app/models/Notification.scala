package models

import play.api.libs.json.{Json, OFormat}

case class Notification(
    id: Long,
    short_code: String,
    notificationType: String,
    notificationStatus: String,
    message: String
)

object Notification {
  implicit val notificationFormat: OFormat[Notification] = Json.format[Notification]
}
