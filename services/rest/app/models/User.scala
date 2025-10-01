package models

import java.sql.Timestamp
import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class User(
    id: Long,
    username: String,
    email: String,
    password: String,
    is_deleted: Boolean,
    created_at: Timestamp,
    updated_at: Timestamp
)

object User {
  implicit val timestampReads: Reads[Timestamp] = Reads.of[Long].map(new Timestamp(_))
  implicit val timestampWrites: Writes[Timestamp] = Writes.of[Long].contramap(_.getTime)

  implicit val userFormat: OFormat[User] = Json.format[User]
}
