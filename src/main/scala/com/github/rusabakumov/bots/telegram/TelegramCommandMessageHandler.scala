package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.connector.TelegramConnector
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.collection.mutable

trait TelegramCommandMessageHandler extends TelegramMessageHandler with Logging {

  def telegramConnector : TelegramConnector
  def commands          : List[TelegramBotCommand]
  def authEnabled       : Boolean
  def authorizedChats   : List[Long]
  def botName           : String

  def defaultKeyboard   : Option[ReplyKeyboardMarkup]
  t 
  private lazy val InitialReplyMarkup = defaultKeyboard.getOrElse(ReplyKeyboardRemoveMarkup(selective = false))

  case class CommandExecution(command: TelegramBotCommand, state: Any)

  private val chatCommandStates = new mutable.HashMap[Long, CommandExecution]

  private lazy val commandMap: Map[String, TelegramBotCommand] = commands.flatMap { command =>
    constructCommandStrings(command, botName) map { commandStr =>
      commandStr -> command
    }
  }.toMap

  def handleMessage(message: Message): Unit = {
    val chatId = message.chat.id
    if (authEnabled && !authorizedChats.contains(chatId)) {
      log.info(s"Got request from unauthorized chat $chatId")
      val reply = MessageToSend(
        chatId,
        "Sorry, but I can only talk in authorized chats. Ask my creator to give you access"
      )
      telegramConnector.sendMessage(reply)
    } else {
      // At first we checking whether any command is present - in this case we will execute it
      // regardless of the chat state
      log.debug(s"Got message from chat $chatId: $message")
      val (messagesToSend, commandExecutionOption: Option[CommandExecution]) = extractCommand(message) match {
        case Some(commandParams) => handleNewCommand(commandParams, message)
        case None => chatCommandStates.get(chatId) match {
          case Some(CommandExecution(command, state)) =>
            val preparedText = trimMentions(message)
            val castedState: command.State = state.asInstanceOf[command.State]

            log.debug(s"Got state: $castedState")
            val (messagesToSend, updatedStateOption) = command.execute(message, preparedText, Some(castedState))

            val commandExecutionOption = updatedStateOption.map(updatedState => CommandExecution(command, updatedState))
            (messagesToSend, commandExecutionOption)

          case None =>
            val reply = MessageToSend(
              message.chat.id,
              "I understand predefined commands only!",
              replyMarkup = defaultKeyboard
            )
            (List(reply), None)
        }
      }

      val checkedMessagesToSend = if (commandExecutionOption.isDefined) {
        fixLastMessageMarkup(messagesToSend)
      } else {
        messagesToSend
      }

      checkedMessagesToSend.foreach(telegramConnector.sendMessage)

      //Updating or clearing state
      commandExecutionOption match {
        case Some(commandExecution) => chatCommandStates.put(chatId, commandExecution)
        case None => chatCommandStates.remove(chatId)
      }
    }
  }

  /**
    * After command is finished, we should reset bot to "clean" state - show default vk if it's defined or clean it
    */
  private def fixLastMessageMarkup(messagesToSend: List[MessageToSend]): List[MessageToSend] = messagesToSend match {
    case msg :: Nil => msg.copy(replyMarkup = Some(InitialReplyMarkup)) :: Nil
    case head :: tail => head :: fixLastMessageMarkup(tail)
    case Nil => Nil
  }

  case class CommandParams(nameString: String, params: String)

  private def extractCommand(message: Message): Option[CommandParams] = {
    val entities = message.entities.toList.flatten

    //We assume that there is at most one command per message
    entities.find(_.entityType == "bot_command") map { commandEntity =>
      val text = message.text.getOrElse("")

      val commandString = text.slice(commandEntity.offset, commandEntity.length)
      val commandParams = text.drop(commandEntity.offset + commandEntity.length + 1) //Trim command and one whitespace

      CommandParams(commandString, commandParams)
    }
  }

  private def trimMentions(message: Message): String = {
    val entities = message.entities.toList.flatten
    val mentions = entities.filter(_.entityType == "mention").sortBy(_.offset)

    val messageText = message.text.getOrElse("")

    //We assume that cannot be nested or overlapping mentions
    val (_, trimmedText) = mentions.foldLeft((0, messageText)) { case ((trimmedSymbols, text), mention) =>
      val updatedText = text.slice(mention.offset - trimmedSymbols, mention.length)
      (trimmedSymbols + mention.length, updatedText)
    }

    trimmedText
  }

  private def handleNewCommand(commandParams: CommandParams, message: Message):
      (List[MessageToSend], Option[CommandExecution]) = {
    commandMap.get(commandParams.nameString) match {
      case Some(command) =>
        val (messagesToSend, stateOption) = command.execute(message, commandParams.params, None)
        val commandExecutionOption = stateOption.map(state => CommandExecution(command, state))
        (messagesToSend, commandExecutionOption)
      case None =>
        (List(MessageToSend(message.chat.id, s"I don't know command $commandParams.nameString!")), None)
    }
  }

  private def constructCommandStrings(command: TelegramBotCommand, botName: String): List[String] = {
    command.names.map("/" + _) ++ command.names.map("/" + _ + "@" + botName)
  }
}
