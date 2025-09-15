package models

import play.api.libs.json.{Json, OFormat}

case class NotificationType(id: Int, name: String)

object NotificationType {
  implicit val notificationTypeFormat: OFormat[NotificationType] = Json.format[NotificationType]
}
