package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.BotContext
import com.github.rusabakumov.bots.telegram.model.Message
import scala.concurrent.{ExecutionContext, Future}

/**
  * Any processing logic for messages. This handler will process messages for many chats, so it should respect chat ids
  * from messages and do not mix them!
  */
trait TelegramMessageHandler {

  def botContext: BotContext

  /** Method that processes message and returns flag whether message was processed or not */
  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean]
}
