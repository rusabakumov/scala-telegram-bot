package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.Message
import scala.concurrent.Future

/** Class incapsulating both the state and it's update logic */
// FIXME Why do we need messageContent field?
trait BotReplyAction {
  def execute(message: Message, messageContent: String, telegramBotContext: BotContext): Future[Boolean]
}
