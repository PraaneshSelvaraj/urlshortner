package models

import java.sql.Timestamp

case class NotificationDTO(id: Long, short_code: String, notificationType: String, notificationStatus: String, message: String, created_at:Timestamp, updated_at: Timestamp)