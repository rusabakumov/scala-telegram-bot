package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.Message

trait TelegramMessageHandler {
  def handleMessage(message: Message): Unit
}
