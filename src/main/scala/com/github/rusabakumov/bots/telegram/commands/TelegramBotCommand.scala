package com.github.rusabakumov.bots.telegram.commands

import com.github.rusabakumov.bots.telegram.connector.TelegramMessageSender
import com.github.rusabakumov.bots.telegram.model.BotState.BotStateUpdateResult
import com.github.rusabakumov.bots.telegram.model._
import scala.concurrent.Future

trait TelegramBotCommand {
  def names: List[String]
}

trait StatelessTelegramBotCommand extends TelegramBotCommand {
  def names: List[String]

  /**
    * Simplified reaction method for commands that will never use state
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    */
  def execute(message: Message, telegramMessageSender: TelegramMessageSender): Future[Unit]
}

trait StatefulTelegramBotCommand extends TelegramBotCommand {
  def names: List[String]

  /**
    * Reacts to the incoming message
    * @param message with command
    * @param commandParams text of the message with trimmed command text
    * @param state state of this command that it set on previous runs
    */
  def execute(
    message: Message
  ): Future[BotStateUpdateResult]
}
