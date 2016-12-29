package com.rusabakumov.bots.telegram

import com.rusabakumov.bots.telegram.model.{Message, MessageToSend}

trait TelegramBotCommand {
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
