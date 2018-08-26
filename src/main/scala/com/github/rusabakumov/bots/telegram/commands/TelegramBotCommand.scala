package com.github.rusabakumov.bots.telegram.commands

import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend}
import scala.concurrent.Future

trait TelegramBotCommand {
  def names: List[String]
}

//TODO make async the whole pipeline
trait TelegramBotCommandSync extends TelegramBotCommand {
  def names: List[String]

  type State

  /**
    * Reacts to the incoming message
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    * @param state state of this command that it set on previous runs
    */
  def execute(message: Message, commandParams: String, state: Option[State]): (List[MessageToSend], Option[State])
}

trait StatelessTelegramBotCommand extends TelegramBotCommandSync {
  def names: List[String]

  type State = Nothing

  /** Wrapper for simplified command */
  final def execute(
    message: Message,
    commandParams: String,
    state: Option[State]
  ): (List[MessageToSend], Option[State]) = (execute(message, commandParams), None)

  /**
    * Simplified reaction method for commands that will never use state
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    */
  def execute(message: Message, commandParams: String): List[MessageToSend]
}

trait TelegramBotCommandAsync extends TelegramBotCommand {
  def names: List[String]

  /** Callback for sending messages when they are ready */
  def telegramMessageSender: TelegramMessageSender

  /**
    * Simplified reaction method for commands that will never use state.
    * It's asynchronous so command can //TODO Understand why
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    */
  def execute(message: Message, commandParams: String): Future[Unit]
}
