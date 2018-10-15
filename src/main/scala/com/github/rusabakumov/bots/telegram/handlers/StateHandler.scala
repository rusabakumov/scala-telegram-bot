package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

class StateHandler(
  stateStorage: BotStateStorage,
  val telegramMessageSender: TelegramMessageSender
) extends TelegramMessageHandler
  with Logging {

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    val chatId = message.chat.id
    val processedOption: Option[Future[Boolean]] = for {
      botMessageState <- stateStorage.getStateForChat(chatId)
    } yield {
      for {
        (messagesToSend, updatedStateOption) <- botMessageState.updateState(message)
        messageSendingResult <- sendMessages(messagesToSend)
      } yield {
        messageSendingResult match {
          case Right(_) =>
            updatedStateOption match {
              case Some(updatedState) =>
                stateStorage.setStateForChat(chatId, updatedState)

              case None =>
                stateStorage.clearStateForChat(chatId)
            }
            true

          case _ =>
            log.error(s"Failed to send response messages to chat $chatId")
            true
        }
      }
    }
    processedOption.getOrElse(Future.successful(false))
  }

  private def sendMessages(
    messagesToSend: List[MessageToSend]
  )(implicit ec: ExecutionContext): Future[Either[String, List[Message]]] = {
    messagesToSend.foldLeft[Future[Either[String, List[Message]]]](Future.successful(Right(List.empty[Message]))) {
      case (result, nextMessage) =>
        result.flatMap {
          case Left(err) => Future.successful(Left(err))
          case Right(sentMessages) =>
            telegramMessageSender.sendMessage(nextMessage).map {
              case Left(err)          => Left(err)
              case Right(sentMessage) => Right(sentMessages.+:(sentMessage))
            }
        }
    }
  }
}
