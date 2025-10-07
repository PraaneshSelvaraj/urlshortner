package dtos

import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class LoginDTO(username: String, password: String)

object LoginDTO {
  implicit val loginDTOFormat: OFormat[LoginDTO] = Json.format[LoginDTO]
}
