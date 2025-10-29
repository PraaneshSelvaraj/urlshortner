package dtos

import java.sql.Timestamp
import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class UserDto(
    id: Long,
    username: String,
    email: String,
    role: String,
    google_id: Option[String],
    auth_provider: String,
    refresh_token: Option[String],
    is_deleted: Boolean,
    created_at: Timestamp,
    updated_at: Timestamp
)

object UserDto {
  implicit val timestampReads: Reads[Timestamp] = Reads.of[Long].map(new Timestamp(_))
  implicit val timestampWrites: Writes[Timestamp] = Writes.of[Long].contramap(_.getTime)

  implicit val userDtoFormat: OFormat[UserDto] = Json.format[UserDto]
}
