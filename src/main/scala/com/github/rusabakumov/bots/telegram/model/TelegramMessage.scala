package com.github.rusabakumov.bots.telegram.model

case class MessageEntity(entityType: String, offset: Int, length: Int)
case class Chat(id: Long, chatType: String, title: Option[String], username: Option[String])

case class Message(
  messageId: Long,
  text: Option[String],
  chat: Chat,
  entities: Option[List[MessageEntity]],
  replyToMessage: Option[Message],
  forwardDate: Option[Int] //To determine whether message is forwarded or not
)

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
