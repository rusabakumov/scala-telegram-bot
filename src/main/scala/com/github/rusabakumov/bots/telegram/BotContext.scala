package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.BotStateTypes.ChatId
import com.github.rusabakumov.bots.telegram.connector.{TelegramAPIError, TelegramConnector}
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

/** Class with all logic for communicating with Telegram servers */
class BotContext(telegramConnector: TelegramConnector) extends Logging {

  implicit val ec: ExecutionContext = telegramConnector.executionContext

  private val botActionsStorage = new BotActionsStorage

  private lazy val DefaultReplyMarkup = ReplyKeyboardRemoveMarkup(selective = false)

  private def sendMessageInternal(
    messageToSend: MessageToSend,
    setDefaultKeyboard: Boolean = false
  ): Future[Either[TelegramAPIError, Message]] = {
    telegramConnector.sendMessage(messageToSend)
  }

  private def sendMessagesInternal(
    messagesToSend: List[MessageToSend],
  ): Future[Either[TelegramAPIError, List[Message]]] = {
    val messagesWithCheckedMarkup = ensureCorrectReplyMarkup(messagesToSend)
    messagesWithCheckedMarkup.foldLeft[Future[Either[TelegramAPIError, List[Message]]]](Future.successful(Right(List.empty[Message]))) {
      case (result, nextMessage) =>
        result.flatMap {
          case Left(err) =>
            Future.successful(Left(err))

          case Right(sentMessages) =>
            sendMessageInternal(nextMessage).map {
              case Left(err)          => Left(err)
              case Right(sentMessage) => Right(sentMessages.+:(sentMessage))
            }
        }
    }
  }

  def getReplyActionForChat(chatId: ChatId): Option[BotReplyAction] = {
    botActionsStorage.getStateForChat(chatId)
  }

  def setChatBotReplyAction(chatId: ChatId, replyAction: BotReplyAction): Unit = {
    botActionsStorage.setStateForChat(chatId, replyAction)
  }

  /**
    * Helper method that sends messages and attaches reply action to last sent message in case of success
    * All messages in a batch should be sent to the same chat!
    * */
  def sendMessages(
    messagesToSend: List[MessageToSend],
    replyActionOption: Option[BotReplyAction] = None
  ): Future[Either[TelegramAPIError, Unit]] = {
    val chatIds = messagesToSend.map(_.chatId).distinct
    if (chatIds.size > 1) {
      log.error(s"Received attempt to send batch of messages with different chat ids! Looks like an error in handlers!")
      Future.successful(Left(TelegramAPIError(0, s"Messages in batch have different chat ids!")))
    } else if (chatIds.isEmpty) {
      Future.successful(Right(List.empty))
    } else {
      val chatId = chatIds.head
      val messageSendingResult: Future[Either[TelegramAPIError, List[Message]]] = sendMessagesInternal(messagesToSend)
      messageSendingResult.map {
        case Right(_) =>
          replyActionOption match {
            case Some(replyAction) =>
              botActionsStorage.setStateForChat(chatId, replyAction)

            case None =>
              botActionsStorage.clearStateForChat(chatId)
          }
          Right(())

        case Left(err) =>
          Left(err)
      }
    }
  }

  /** Helper method that sends messages and attaches reply action to last sent message in case of success */
  def sendMessage(
    messageToSend: MessageToSend,
    replyActionOption: Option[BotReplyAction] = None
  ): Future[Either[TelegramAPIError, Unit]] = {
    sendMessages(List(messageToSend), replyActionOption)
  }

  private def ensureCorrectReplyMarkup(messagesToSend: List[MessageToSend]): List[MessageToSend] = messagesToSend match {
    case msg :: Nil if msg.replyMarkup.isEmpty => msg.copy(replyMarkup = Some(DefaultReplyMarkup)) :: Nil
    case msg :: Nil => msg :: Nil
    case head :: tail => head :: ensureCorrectReplyMarkup(tail)
    case Nil => Nil
  }
}
