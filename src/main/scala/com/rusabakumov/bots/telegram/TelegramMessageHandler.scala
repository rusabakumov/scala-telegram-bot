package com.rusabakumov.bots.telegram

import com.rusabakumov.bots.telegram.model.Message

trait TelegramMessageHandler {
  def handleMessage(message: Message): Unit
}
