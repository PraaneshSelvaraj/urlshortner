package dtos

import play.api.libs.json.{Json, OFormat}

case class LoginDTO(email: String, password: String)

object LoginDTO {
  implicit val loginDTOFormat: OFormat[LoginDTO] = Json.format[LoginDTO]
}
