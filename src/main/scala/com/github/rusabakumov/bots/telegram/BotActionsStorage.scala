package com.github.rusabakumov.bots.telegram

import scala.collection.parallel.mutable
import BotStateTypes._

class BotActionsStorage() {
  private val chatStateMap = new mutable.ParHashMap[ChatId, BotReplyAction]

  def getStateForChat(chatId: ChatId): Option[BotReplyAction] = {
    chatStateMap.get(chatId)
  }

  def setStateForChat(chatId: ChatId, botState: BotReplyAction): Unit = {
    chatStateMap.put(chatId, botState)
  }

  def clearStateForChat(chatId: ChatId): Unit = {
    chatStateMap.remove(chatId)
  }
}

object BotStateTypes {
  type ChatId = Long
  type MessageId = Long
}
