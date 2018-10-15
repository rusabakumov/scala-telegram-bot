package com.github.rusabakumov.bots.telegram.connector

import com.github.rusabakumov.bots.telegram.model._
import scala.concurrent.Future

/** Interface allowing sending messages */
//TODO it should be created by method in telegramConnector
class TelegramMessageSender(telegramConnector: TelegramConnector) {

//  def defaultKeyboard: Option[ReplyKeyboardMarkup] = None
//
//  private lazy val InitialReplyMarkup = defaultKeyboard.getOrElse(ReplyKeyboardRemoveMarkup(selective = false))
  def sendMessage(messageToSend: MessageToSend, setDefaultKeyboard: Boolean = false): Future[Either[String, Message]] = {
    telegramConnector.sendMessage(messageToSend)
  }

//  /**
//    * After command is finished, we should reset bot to "clean" state - show default vk if it's defined or clean it
//    */
//  private def setDefaultKeyboard(messagesToSend: List[MessageToSend]): List[MessageToSend] = messagesToSend match {
//    case msg :: Nil   => msg.copy(replyMarkup = Some(InitialReplyMarkup)) :: Nil
//    case head :: tail => head :: setDefaultKeyboard(tail)
//    case Nil          => Nil
//  }
}
