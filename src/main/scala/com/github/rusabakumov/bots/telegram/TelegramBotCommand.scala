package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend}

trait TelegramBotCommand {
  def names: List[String]
}

trait TelegramBotCommandSync extends TelegramBotCommand {
  def names: List[String]

  type State

  /**
    * Reacts to the incoming message
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    * @param state state of this command that it set on previous runs
    */
  def execute(message: Message, commandParams: String, state: Option[State]):
    (List[MessageToSend], Option[State])
}

trait StatelessTelegramBotCommand extends TelegramBotCommand {
  def names: List[String]

  type State = Nothing

  /** Wrapper for simplified command */
  final def execute(message: Message, commandParams: String, state: Option[State]):
    (List[MessageToSend], Option[State]) = (execute(message, commandParams), None)

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
  def sendMessage(message: MessageToSend): Unit

  /**
    * Simplified reaction method for commands that will never use state
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    */
  def execute(message: Message, commandParams: String): Unit
}
