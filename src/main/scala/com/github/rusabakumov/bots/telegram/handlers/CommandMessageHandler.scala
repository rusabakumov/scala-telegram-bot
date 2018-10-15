package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.commands._
import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * Not thread safe!
  */
class CommandMessageHandler(
  commands: List[TelegramBotCommand],
  botName: String,
  val telegramMessageSender: TelegramMessageSender,
  stateStorage: BotStateStorage
) extends TelegramMessageHandler
  with Logging {

  private lazy val commandMap: Map[String, TelegramBotCommand] = commands.flatMap { command =>
    constructCommandStrings(command, botName).map { commandStr =>
      commandStr -> command
    }
  }.toMap

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    val chatId = message.chat.id
    log.debug(s"Got message from chat $chatId: $message")
    extractCommand(message) match {
      case Some(commandParams) =>
        commandMap.get(commandParams.nameString) match {
          case Some(command: StatefulTelegramBotCommand) =>
            command.execute(message, commandParams.params).flatMap {
              case (messagesToSend, stateOption) =>
                processSyncCommandResults(
                  chatId,
                  messagesToSend,
                  stateOption
                )
            }

          case Some(command: StatelessTelegramBotCommand) =>
            command.execute(message, commandParams.params, telegramMessageSender).map(_ => true)

          case _ =>
            Future.successful(false)
        }

      case None =>
        //User haven't replied to previous state, so we wouldn't process message
        Future.successful(false)
    }
  }

  private def processSyncCommandResults(
    chatId: Long,
    messagesToSend: List[MessageToSend],
    stateOption: Option[BotState]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val messageSendingResult: Future[Either[String, List[Message]]] = sendMessages(messagesToSend)

    messageSendingResult.map {
      case Right(_) =>
        stateOption match {
          case Some(updatedState) =>
            stateStorage.setStateForChat(chatId, updatedState)

          case None =>
            stateStorage.clearStateForChat(chatId)
        }
        true

      case _ =>
        true
    }
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

  case class CommandParams(nameString: String, params: String)

  private def extractCommand(message: Message): Option[CommandParams] = {
    val entities = message.entities.toList.flatten

    //We assume that there is at most one command per message
    entities.find(_.entityType == "bot_command").map { commandEntity =>
      val text = message.rawText.getOrElse("")

      val commandString = text.slice(commandEntity.offset, commandEntity.length)
      val commandParams = text.drop(commandEntity.offset + commandEntity.length + 1) //Trim command and one whitespace

      CommandParams(commandString, commandParams)
    }
  }

  private def constructCommandStrings(command: TelegramBotCommand, botName: String): List[String] = {
    command.names.map("/" + _) ++ command.names.map("/" + _ + "@" + botName)
  }

}
