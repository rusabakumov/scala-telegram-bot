package com.github.rusabakumov.bots.telegram.model

import com.github.rusabakumov.bots.telegram.model.BotState.BotStateUpdateResult
import com.github.rusabakumov.bots.telegram.model.BotStateTypes._
import scala.collection.parallel.mutable
import scala.concurrent.Future

//case class BotState[T](
//  state: T,
//  stateUpdater: BotStateHandler[T]
//)

/** Class incapsulating both the state and it's update logic */
trait BotState {
  def updateState(message: Message, messageContent: String): Future[BotStateUpdateResult]
}

object BotState {
  sealed trait BotStateUpdateResult
  case object BotStateNotUpdated
  case class BotStateUpdated(
    messagesToSend: List[MessageToSend],
    updatedState: Option[BotState]
  )
}

class BotStateStorage() {
  private val messageStateMap = new mutable.ParHashMap[ChatId, BotState]

  def getStateForChat(chatId: ChatId): Option[BotState] = {
    messageStateMap.get(chatId)
  }

  def setStateForChat(chatId: ChatId, botState: BotState): Unit = {
    messageStateMap.put(chatId, botState)
  }

  def clearStateForChat(chatId: ChatId): Unit = {
    messageStateMap.remove(chatId)
  }
}

object BotStateTypes {
  type ChatId = Long
  type MessageId = Long
}
