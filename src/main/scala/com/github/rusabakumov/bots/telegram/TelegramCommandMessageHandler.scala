package com.github.rusabakumov.bots.telegram

import akka.actor.ActorSystem
import com.github.rusabakumov.bots.telegram.connector.TelegramConnector
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait TelegramCommandMessageHandler extends TelegramMessageHandler with Logging {

  def telegramConnector : TelegramConnector
  def commands          : List[TelegramBotCommand]
  def authEnabled       : Boolean
  def authorizedChats   : List[Long]
  def botName           : String

  def defaultKeyboard   : Option[ReplyKeyboardMarkup]

  implicit val system = ActorSystem("telegram-connector")
  implicit val executionContext: ExecutionContext = system.dispatcher

  private lazy val InitialReplyMarkup = defaultKeyboard.getOrElse(ReplyKeyboardRemoveMarkup(selective = false))

  case class CommandExecution(command: TelegramBotCommand, state: Any)

  type SetChatCommandStateCallback = (Long, CommandExecution) => Unit

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
      val (result, resultFutureOpt, commandOpt: Option[TelegramBotCommand]) = extractCommand(message) match {
        case Some(commandParams) =>
          log.debug(s"extracted params for new command: $commandParams")
          handleNewCommand(commandParams, message)

        case None => chatCommandStates.get(chatId) match {
          case Some(CommandExecution(command, state)) =>
            val preparedText = trimMentions(message)
            val castedState: command.State = state.asInstanceOf[command.State]

            log.debug(s"Got state: $castedState")
            val (result, resultFutureOpt) = command.execute(message, preparedText, Some(castedState))

            (result, resultFutureOpt, Some(command))

          case None =>
            val reply = MessageToSend(
              message.chat.id,
              "I understand predefined commands only!",
              replyMarkup = defaultKeyboard
            )
            (ExecutionResult(List(reply)), None, None)
        }
      }

      processExecutionResult(chatId, result, commandOpt)

      resultFutureOpt.map { resultFuture =>
        resultFuture.map { result =>
          processExecutionResult(chatId, result, commandOpt)
        }
      }
    }
  }

  private def processExecutionResult(chatId: Long, result: ExecutionResult, commandOpt: Option[TelegramBotCommand]) = {
    val commandExecutionOption = result.state.flatMap { updatedState =>
      commandOpt.map(command => CommandExecution(command, updatedState))
    }
    
    val messagesWithCheckedKeyboard = if (result.setDefaultKeyboard) {
      setDefaultKeyboard(result.messages)
    } else {
      result.messages
    }

    messagesWithCheckedKeyboard.foreach(telegramConnector.sendMessage)

    //Updating or clearing state
    commandExecutionOption match {
      case Some(commandExecution) => chatCommandStates.put(chatId, commandExecution)
      case None => chatCommandStates.remove(chatId)
    }
  }

  def handleCallbackQuery(query: CallbackQuery): Unit = {
    val text = query.data.getOrElse("")

    val answer = query.message match {
      case Some(message) =>
        val chatId = message.chat.id

        val (resultOpt, resultFutureOpt, commandOpt) = extractCallbackCommand(text) match {
          case Some(commandParams) =>
            log.debug(s"extracted params for new command: $commandParams")
            val (result, resultFutureOpt, commandOpt) = handleNewCommand(commandParams, message)

            (Some(result), resultFutureOpt, commandOpt)

          case None =>
            chatCommandStates.get(chatId) match {
              case Some(CommandExecution(command, state)) =>
                log.debug(s"Get stored command: $command")
                val castedState: command.State = state.asInstanceOf[command.State]

                val (result, resultFutureOpt) = command.execute(message, text, Some(castedState))

                (Some(result), resultFutureOpt, Some(command))

              case None =>
                log.debug(s"No command found, ignore")
                (None, None, None)
            }
        }

        resultOpt.map { result =>
          editCallbackMessage(chatId, message.messageId, result, commandOpt)
        }

        resultFutureOpt.map { resultFuture =>
          resultFuture.map { result =>
            editCallbackMessage(chatId, message.messageId, result, commandOpt)
          }
        }

        AnswerCallbackQuery(query.id)

      case None => AnswerCallbackQuery(query.id)
    }

    telegramConnector.answerCallbackQuery(answer)
  }

  private def editCallbackMessage(chatId: Long, messageId: Long, result: ExecutionResult, commandOpt: Option[TelegramBotCommand]) = {
    val commandExecutionOption = result.state.flatMap { updatedState =>
      commandOpt.map(command => CommandExecution(command, updatedState))
    }

    commandExecutionOption match {
      case Some(commandExecution) => chatCommandStates.put(chatId, commandExecution)
      case None => Unit //chatCommandStates.remove(chatId)
    }

    result.messages.headOption match {
      case Some(messageToSend) =>
        val edit = EditMessageText(
          chatId,
          messageId,
          text = messageToSend.text,
          parseMode = messageToSend.parseMode,
          replyMarkup = messageToSend.replyMarkup
        )
        telegramConnector.editMessageText(edit)

      case None =>
        if (result.deleteMessage) {
          telegramConnector.deleteMessage(chatId, messageId)
        } else {
          val edit = EditMessageReplyMarkup(
            chatId = Some(chatId),
            messageId = Some(messageId),
            replyMarkup = Some(InlineKeyboardMarkup(Nil))
          )
          telegramConnector.editMessageReplyMarkup(edit)
        }
    }
  }

  /**
    * Used to update particular chat state without reaction to user message, but in "push" mode.
    * For example, by scheduled event ?*
    */
  protected def setCommandState: SetChatCommandStateCallback = { case (chatId, commandExecution) =>
    chatCommandStates.put(chatId, commandExecution)
  }

  /**
    * After command is finished, we should reset bot to "clean" state - show default vk if it's defined or clean it
    */
  private def setDefaultKeyboard(messagesToSend: List[MessageToSend]): List[MessageToSend] = messagesToSend match {
    case msg :: Nil => msg.copy(replyMarkup = Some(InitialReplyMarkup)) :: Nil
    case head :: tail => head :: setDefaultKeyboard(tail)
    case Nil => Nil
  }

  case class CommandParams(nameString: String, params: String)

  private def extractCommand(message: Message): Option[CommandParams] = {
    val entities = message.entities.toList.flatten

    //We assume that there is at most one command per message
    entities.find(_.entityType == "bot_command") map { commandEntity =>
      val text = message.text.getOrElse("")

      val commandString = text.slice(commandEntity.offset, commandEntity.offset + commandEntity.length)
      val commandParams = text.drop(commandEntity.offset + commandEntity.length + 1) //Trim command and one whitespace

      CommandParams(commandString, commandParams)
    }
  }

  private def extractCallbackCommand(text: String): Option[CommandParams] = {
    if (text.trim().startsWith("/")) {
      text.split(" ", 2) match {
        case Array(commandString, commandParams) => Some(CommandParams(commandString, commandParams))
        case Array(commandString) => Some(CommandParams(commandString, "")) 
      }
    } else {
      None
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
      (ExecutionResult, Option[Future[ExecutionResult]], Option[TelegramBotCommand]) = {
    commandMap.get(commandParams.nameString) match {
      case Some(command: TelegramBotCommand) =>
        val (result, resultFutureOpt) = command.execute(message, commandParams.params, None)
        (result, resultFutureOpt, Some(command))

      case _ =>
        val result = ExecutionResult(List(MessageToSend(message.chat.id, s"I don't know command ${commandParams.nameString}!")))
        (result, None, None)
    }
  }

  private def constructCommandStrings(command: TelegramBotCommand, botName: String): List[String] = {
    command.names.map("/" + _) ++ command.names.map("/" + _ + "@" + botName)
  }
}
