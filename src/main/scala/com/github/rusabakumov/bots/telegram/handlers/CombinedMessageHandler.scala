package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.BotContext
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend}
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

/** Processes incoming message passing it through given list of handlers until it will be processed
  * Handlers called sequentially
  */
class CombinedMessageHandler(
  handlers: List[TelegramMessageHandler],
  val telegramBotContext: BotContext
) extends TelegramMessageHandler with Logging {

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    val handlersResult = handlers.foldLeft(Future.successful(false)) {
      case (processedFuture, nextHandler) =>
        processedFuture.flatMap {
          case true => Future.successful(true)
          case false => nextHandler.handleMessage(message)
        }
    }

    handlersResult.flatMap {
      case false =>
        val fallbackMessage = MessageToSend(
          message.chat.id,
          s"Sorry, can't process this",
          None
        )
        telegramBotContext.sendMessage(message.chat.id, fallbackMessage).map {
          case Right(_) =>
            true

          case Left(err) =>
            throw new IllegalStateException(s"Was unable to sent even fallback message because of: $err")
        }

      case true =>
        Future.successful(true)
    }
  }
}
