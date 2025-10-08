package dtos

import play.api.libs.json.{Json, OFormat}

case class GoogleLoginDTO(idToken: String)

object GoogleLoginDTO {
  implicit val googleLoginFormat: OFormat[GoogleLoginDTO] = Json.format[GoogleLoginDTO]
}
