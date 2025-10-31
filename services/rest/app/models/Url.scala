package models

import play.api.libs.json.{Json, OFormat, Reads, Writes}
import java.sql.Timestamp

case class Url(
    id: Long,
    user_id: Long,
    short_code: String,
    long_url: String,
    clicks: Int,
    is_deleted: Boolean,
    created_at: Timestamp,
    updated_at: Timestamp,
    expires_at: Timestamp
)

object Url {
  implicit val timestampReads: Reads[Timestamp] = Reads.of[Long].map(new Timestamp(_))
  implicit val timestampWrites: Writes[Timestamp] = Writes.of[Long].contramap(_.getTime)

  implicit val urlFormat: OFormat[Url] = Json.format[Url]
}
