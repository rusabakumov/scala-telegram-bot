package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.BotContext
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend}
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

class AuthMessageHandler(
  authorizedChats: List[Long],
  val telegramBotContext: BotContext
) extends TelegramMessageHandler with Logging {

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (!authorizedChats.contains(message.chat.id)) {
      log.info(s"Got request from unauthorized chat ${message.chat.id}")
      val reply = MessageToSend(
        message.chat.id,
        "Sorry, but I can only talk in authorized chats. Ask my creator to give you access"
      )
      telegramBotContext.sendMessage(message.chat.id, reply)
      Future.successful(true)
    } else {
      Future.successful(false)
    }
  }
}
