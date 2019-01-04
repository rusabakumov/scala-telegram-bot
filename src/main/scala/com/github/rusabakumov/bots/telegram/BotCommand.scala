package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.Message
import scala.concurrent.Future

trait BotCommand extends BotReplyAction {
  def names: List[String]

  /**
  * Simplified reaction method for commands that will never use state
  * @param message with command
  * @param commandParams text of the message with trimmed command text
  */
  override def execute(
    message: Message,
    commandParams: String,
    botContext: BotContext
  ): Future[Boolean]
}
