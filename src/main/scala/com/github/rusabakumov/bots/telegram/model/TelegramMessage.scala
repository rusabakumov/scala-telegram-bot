package com.github.rusabakumov.bots.telegram.model

case class MessageEntity(entityType: String, offset: Int, length: Int)
case class Chat(id: Long, chatType: String, title: Option[String], username: Option[String])

case class Message(
  messageId: Long,
  rawText: Option[String],
  chat: Chat,
  entities: Option[List[MessageEntity]],
  replyToMessage: Option[Message],
  forwardDate: Option[Int] //To determine whether message is forwarded or not
) {

  lazy val text: String = {
    val entitiesList = entities.toList.flatten
    val mentions = entitiesList.filter(_.entityType == "mention").sortBy(_.offset)

    val messageText = rawText.getOrElse("")

    //We assume that cannot be nested or overlapping mentions
    val (_, trimmedText) = mentions.foldLeft((0, messageText)) {
      case ((trimmedSymbols, text), mention) =>
        val updatedText = text.slice(mention.offset - trimmedSymbols, mention.length)
        (trimmedSymbols + mention.length, updatedText)
    }

    trimmedText
  }
}

case class MessageToSend(
  chatId: Long,
  text: String,
  replyToMessageId: Option[Long] = None,
  parseMode: Option[String] = None,
  replyMarkup: Option[ReplyMarkup] = None)

sealed trait ReplyMarkup

final case class ForceReplyMarkup(selective: Boolean) extends ReplyMarkup

final case class ReplyKeyboardRemoveMarkup(selective: Boolean) extends ReplyMarkup

final case class ReplyKeyboardMarkup(
  buttons: List[List[KeyboardButton]],
  resizeKeyboard: Boolean = false,
  oneTimeKeyboard: Boolean = false,
  selective: Boolean = false
) extends ReplyMarkup

case class KeyboardButton(text: String, requestContact: Boolean = false, requestLocaton: Boolean = false)
