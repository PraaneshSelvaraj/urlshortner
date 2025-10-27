package exceptions

class InvalidUrlException extends UrlShortnerException {
  override def getMessage: String = "Url shortnenig is not allowed for this URL"
}
