package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.{BotCommand, BotContext}
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.concurrent.{ExecutionContext, Future}

class CommandMessageHandler(
  commands: List[BotCommand],
  botName: String,
  val telegramBotContext: BotContext
) extends TelegramMessageHandler
  with Logging {

  private lazy val commandMap: Map[String, BotCommand] = commands.flatMap { command =>
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
          case Some(command) =>
            command.execute(message, commandParams.params, telegramBotContext).map(_ => true)

          case _ =>
            Future.successful(false)
        }

      case None =>
        //User haven't replied to previous state, so we wouldn't process message
        Future.successful(false)
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

  private def constructCommandStrings(command: BotCommand, botName: String): List[String] = {
    command.names.map("/" + _) ++ command.names.map("/" + _ + "@" + botName)
  }

}
