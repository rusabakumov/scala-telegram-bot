package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.BotContext
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

/** Checks whether we have already set reply action for chat and executes it */
class ReplyActionHandler(
  val botContext: BotContext
) extends TelegramMessageHandler with Logging {

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    val chatId = message.chat.id
    botContext.getReplyActionForChat(chatId) match {
      case Some(action) =>
        action.execute(message, botContext)
        Future.successful(true)

      case None =>
        Future.successful(false)
    }
  }
}
