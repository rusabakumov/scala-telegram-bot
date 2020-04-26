package com.github.rusabakumov.bots.telegram.connector

/** Information about returned error on api method call
  *
  * If some internal error happened, code is set to 0, and description is filled with details
  *
  * */
case class TelegramAPIError(code: Int, description: String) {

  override def toString: String = {
    if (code == 0) {
      s"Can't perform request to telegram API: $description"
    } else {
      s"Telegram API error: $code - $description"
    }
  }
}

object TelegramAPIError {

  def requestError(): TelegramAPIError = TelegramAPIError(0, "Failed to perform request")
}
