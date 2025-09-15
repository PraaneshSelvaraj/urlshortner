package dtos

import play.api.libs.json.{Json, OFormat}

case class UrlDto(url: String)

object UrlDto {
  implicit val urlDtoFormat: OFormat[UrlDto] = Json.format[UrlDto]
}
