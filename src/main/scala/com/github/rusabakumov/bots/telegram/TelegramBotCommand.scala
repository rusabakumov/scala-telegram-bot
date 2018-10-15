package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend}
import scala.concurrent.Future

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
    (ExecutionResult, Option[Future[ExecutionResult]])
}

trait StatelessTelegramBotCommand extends TelegramBotCommand {
  def names: List[String]

  type State = Nothing

  /** Wrapper for simplified command */
  final def execute(message: Message, commandParams: String, state: Option[State]):
    (ExecutionResult, Option[Future[ExecutionResult]]) = (ExecutionResult(execute(message, commandParams), None), None)

  /**
    * Simplified reaction method for commands that will never use state
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    */
  def execute(message: Message, commandParams: String): List[MessageToSend]
}

case class ExecutionResult(
  messages: List[MessageToSend],
  state: Option[Any] = None,
  deleteMessage: Boolean = false,
  setDefaultKeyboard: Boolean = false
)
