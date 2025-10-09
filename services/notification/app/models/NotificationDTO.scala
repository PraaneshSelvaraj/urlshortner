package models

import java.sql.Timestamp

case class NotificationDTO(
    id: Long,
    short_code: Option[String],
    user_id: Option[Long],
    notificationType: String,
    notificationStatus: String,
    message: String,
    created_at: Timestamp,
    updated_at: Timestamp
)
