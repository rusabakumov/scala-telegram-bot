package com.github.rusabakumov.bots.telegram.model

case class TelegramUpdate(
  updateId: Long,
  message: Option[Message],
  inlineQuery: Option[InlineQuery]
)

case class InlineQuery(
  id: String,
  user: TelegramUser,
  query: String,
  offset: String
)

case class TelegramUser(
  id: String
)

case class AnswerInlineQuery(
  inlineQueryId: String,
  results: List[InlineQueryResult],
  isPersonal: Boolean = true
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

