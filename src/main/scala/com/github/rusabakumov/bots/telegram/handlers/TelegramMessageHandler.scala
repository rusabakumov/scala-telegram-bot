package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model.Message
import scala.concurrent.{ExecutionContext, Future}

trait TelegramMessageHandler {

  def telegramMessageSender: TelegramMessageSender

  /** Method that processes message and returns flag whether message was processed or not */
  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean]
}
