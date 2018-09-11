package com.github.rusabakumov.bots.telegram.model

case class TelegramUpdate(
  updateId: Long,
  message: Option[Message],
  inlineQuery: Option[InlineQuery],
  callbackQuery: Option[CallbackQuery]
)

case class InlineQuery(
  id: String,
  from: TelegramUser,
  query: String,
  offset: String
)

case class CallbackQuery(
  id: String,
  from: TelegramUser,
  message: Option[Message] = None,
  inlineMessageId: Option[String] = None,
  chatInstance: Option[String] = None,
  data: Option[String] = None,
  gameShortName: Option[String] = None
)

case class TelegramUser(
  id: Int,
  isBot: Boolean,
  firstName: String,
  lastName: Option[String] = None,
  username: Option[String] = None,
  languageCode: Option[String] = None
)

case class AnswerInlineQuery(
  inlineQueryId: String,
  results: List[InlineQueryResult],
  isPersonal: Boolean = true
)

case class AnswerCallbackQuery(
  callbackQueryId: String,
  text: Option[String] = None,
  showAlert: Boolean = false,
  url: Option[String] = None,
  cacheTime: Option[Int] = None
)

case class EditMessageText(
  chatId: Long,
  messageId: Long,
  text: String,
  parseMode: Option[String] = None,
  replyMarkup: Option[ReplyMarkup] = None
)

case class EditMessageReplyMarkup(
  chatId: Option[Long] = None,
  messageId: Option[Long] = None,
  inlineMessageId: Option[String] = None,
  replyMarkup: Option[ReplyMarkup] = None
)

case class DeleteMessage(
  chatId: Long,
  messageId: Long  
)

sealed trait InlineQueryResult
final case class InlineQueryResultContact(
  id: String,
  resultType: String = "contact",
  phoneNumber: String = "+71234567890",
  firstName: String = "Allo"
) extends InlineQueryResult

sealed trait InputMessageContent
final case class InputTextMessageContent (
  messageText: String,
  parseMode: Option[String] = None,
  disableWebPagePreview: Option[Boolean] = None
) extends InputMessageContent

case class ChosenInlineResult(
  resultId: String,
  from: TelegramUser,
  query: String,
  inlineMessageId: Option[String]
)

