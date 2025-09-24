package exceptions

class UrlExpiredException extends UrlShortnerException {
  override def getMessage: String = "Url Expired"
}
