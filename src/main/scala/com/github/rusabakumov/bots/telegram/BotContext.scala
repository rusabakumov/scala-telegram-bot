package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.connector.TelegramConnector
import com.github.rusabakumov.bots.telegram.BotStateTypes.ChatId
import com.github.rusabakumov.bots.telegram.model._
import scala.concurrent.{ExecutionContext, Future}

/** Interface allowing sending messages */
//TODO it should be created by method in telegramConnector
class BotContext(telegramConnector: TelegramConnector) {

  implicit val ec: ExecutionContext = telegramConnector.executionContext

  private val botActionsStorage = new BotActionsStorage

//  def defaultKeyboard: Option[ReplyKeyboardMarkup] = None
//
//  private lazy val InitialReplyMarkup = defaultKeyboard.getOrElse(ReplyKeyboardRemoveMarkup(selective = false))
  private def sendMessageInternal(
    messageToSend: MessageToSend,
    setDefaultKeyboard: Boolean = false
  ): Future[Either[String, Message]] = {
    telegramConnector.sendMessage(messageToSend)
  }

  private def sendMessagesInternal(
    messagesToSend: List[MessageToSend],
  ): Future[Either[String, List[Message]]] = {
    messagesToSend.foldLeft[Future[Either[String, List[Message]]]](Future.successful(Right(List.empty[Message]))) {
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

  def setChatBotReplyAction(chatId: ChatId, replyAction: BotReplyAction): Unit = {
    botActionsStorage.setStateForChat(chatId, replyAction)
  }

  /** Helper method that sends messages and attaches reply action to last sent message in case of success */
  def sendMessages(
    chatId: Long,
    messagesToSend: List[MessageToSend],
    replyActionOption: Option[BotReplyAction] = None
  ): Future[Either[String, List[Message]]] = {
    val messageSendingResult: Future[Either[String, List[Message]]] = sendMessagesInternal(messagesToSend)

    messageSendingResult.map {
      case Right(msgs) =>
        replyActionOption match {
          case Some(replyAction) =>
            botActionsStorage.setStateForChat(chatId, replyAction)

          case None =>
            botActionsStorage.clearStateForChat(chatId)
        }
        Right(msgs)

      case any =>
        any
    }
  }

  /** Helper method that sends messages and attaches reply action to last sent message in case of success */
  def sendMessage(
    chatId: Long,
    messageToSend: MessageToSend,
    replyActionOption: Option[BotReplyAction] = None
  ): Future[Either[String, Message]] = {
    sendMessages(chatId, List(messageToSend), replyActionOption).map {
      case Right(message :: _) => Right(message)
      case Right(_) => Left("Error decoding sending result")
      case Left(err) => Left(err)
    }
  }

//  def setMessageBotReplyAction(messageId: MessageId, replyAction: BotReplyAction): Unit

//  /**
//    * After command is finished, we should reset bot to "clean" state - show default vk if it's defined or clean it
//    */
//  private def setDefaultKeyboard(messagesToSend: List[MessageToSend]): List[MessageToSend] = messagesToSend match {
//    case msg :: Nil   => msg.copy(replyMarkup = Some(InitialReplyMarkup)) :: Nil
//    case head :: tail => head :: setDefaultKeyboard(tail)
//    case Nil          => Nil
//  }
}
