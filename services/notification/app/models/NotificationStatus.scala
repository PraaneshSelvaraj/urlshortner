package models

import play.api.libs.json.{Json, OFormat}

case class NotificationStatus(id: Int, name: String)

object NotificationStatus {
  implicit val notificationStatusFormat: OFormat[NotificationStatus] =
    Json.format[NotificationStatus]
}
