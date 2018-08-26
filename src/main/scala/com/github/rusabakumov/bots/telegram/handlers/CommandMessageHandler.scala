package com.github.rusabakumov.bots.telegram.handlers

import com.github.rusabakumov.bots.telegram.commands.{TelegramBotCommand, TelegramBotCommandAsync, TelegramBotCommandSync}
import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class CommandMessageHandler(
  commands: List[TelegramBotCommand],
  botName: String,
  val telegramMessageSender: TelegramMessageSender
) extends TelegramMessageHandler
  with Logging {

  case class CommandExecution(command: TelegramBotCommandSync, state: Any, lastMessageId: Long)

  type SetChatCommandStateCallback = (Long, CommandExecution) => Unit

  private val chatCommandStates = new mutable.HashMap[Long, CommandExecution]

  private lazy val commandMap: Map[String, TelegramBotCommand] = commands.flatMap { command =>
    constructCommandStrings(command, botName).map { commandStr =>
      commandStr -> command
    }
  }.toMap

  def handleMessage(message: Message)(implicit ec: ExecutionContext): Future[Boolean] = {
    val chatId = message.chat.id
    // At first we checking whether any command is present - in this case we will execute it
    // regardless of the chat state
    log.debug(s"Got message from chat $chatId: $message")
    extractCommand(message) match {
      case Some(commandParams) =>
        commandMap.get(commandParams.nameString) match {
          case Some(command: TelegramBotCommandSync) =>
            val (messagesToSend, stateOption) = command.execute(message, commandParams.params, None)
            processSyncCommandResults(
              chatId,
              command,
              messagesToSend,
              stateOption
            )

          case Some(command: TelegramBotCommandAsync) =>
            command.execute(message, commandParams.params)
            Future.successful(true)

          case _ =>
            Future.successful(false)
        }

      case None =>
        chatCommandStates.get(chatId) match {
          case Some(CommandExecution(command, state, lastMessageId))
            if message.replyToMessage.exists(_.messageId == lastMessageId) =>

            val preparedText = trimMentions(message)
            val castedState: command.State = state.asInstanceOf[command.State]

            log.debug(s"Got state: $castedState")
            val (messagesToSend, updatedStateOption) = command.execute(message, preparedText, Some(castedState))

            processSyncCommandResults(
              chatId,
              command,
              messagesToSend,
              updatedStateOption
            )

          case None =>
            //User haven't replied to previous state, so we wouldn't process message
            Future.successful(false)
        }
    }
  }

  private def processSyncCommandResults(
    chatId: Long,
    command: TelegramBotCommandSync,
    messagesToSend: List[MessageToSend],
    stateOption: Option[Any]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val messageSendingResult: Future[Either[String, List[Message]]] = sendMessages(messagesToSend)

    messageSendingResult.map {
      case Right(messages) =>
        val commandExecutionOption = stateOption.map { updatedState =>
          CommandExecution(command, updatedState, messages.last.messageId)
        }

        //Updating or clearing state
        commandExecutionOption match {
          case Some(commandExecution) => chatCommandStates.put(chatId, commandExecution)
          case None                   => chatCommandStates.remove(chatId)
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

  /**
    * Used to update particular chat state without reaction to user message, but in "push" mode.
    * For example, by scheduled event ?*
    */
  protected def setCommandState: SetChatCommandStateCallback = {
    case (chatId, commandExecution) =>
      chatCommandStates.put(chatId, commandExecution)
  }

  case class CommandParams(nameString: String, params: String)

  private def extractCommand(message: Message): Option[CommandParams] = {
    val entities = message.entities.toList.flatten

    //We assume that there is at most one command per message
    entities.find(_.entityType == "bot_command").map { commandEntity =>
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
    val (_, trimmedText) = mentions.foldLeft((0, messageText)) {
      case ((trimmedSymbols, text), mention) =>
        val updatedText = text.slice(mention.offset - trimmedSymbols, mention.length)
        (trimmedSymbols + mention.length, updatedText)
    }

    trimmedText
  }

  private def constructCommandStrings(command: TelegramBotCommand, botName: String): List[String] = {
    command.names.map("/" + _) ++ command.names.map("/" + _ + "@" + botName)
  }

}
