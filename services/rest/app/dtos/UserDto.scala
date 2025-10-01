package dtos

import play.api.libs.json.{Json, OFormat}

case class CreateUserDTO(username: String, email: String, password: String)

object CreateUserDTO {
  implicit val createUserDTOFormat: OFormat[CreateUserDTO] = Json.format[CreateUserDTO]
}
