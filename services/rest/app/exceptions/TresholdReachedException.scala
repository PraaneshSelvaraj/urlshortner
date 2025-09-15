package exceptions

class TresholdReachedException extends UrlShortnerException {
  override def getMessage: String = "Treshold Reached for the url"
}
