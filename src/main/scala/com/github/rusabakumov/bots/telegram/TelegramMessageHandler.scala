package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.{Message, CallbackQuery}

trait TelegramMessageHandler {
  def handleMessage(message: Message): Unit
  def handleCallbackQuery(query: CallbackQuery): Unit
}
