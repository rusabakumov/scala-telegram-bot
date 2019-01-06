package com.github.rusabakumov.bots.telegram

import com.github.rusabakumov.bots.telegram.model.Message
import scala.concurrent.Future

/** Class incapsulating both the state and it's update logic */
trait BotReplyAction {
  def execute(message: Message, botContext: BotContext): Future[Boolean]
}
