package com.github.rusabakumov.bots.telegram.model

import argonaut.Argonaut._
import argonaut._

object TelegramModelCodecs {

  implicit def chatCodecJson: CodecJson[Chat] = casecodec4(
    Chat.apply, Chat.unapply
  )("id", "type", "title", "username")

  implicit def messageEntityCodecJson: CodecJson[MessageEntity] = casecodec3(
    MessageEntity.apply, MessageEntity.unapply
  )("type", "offset", "length")

  implicit def messageCodecJson: CodecJson[Message] = casecodec5(
    Message.apply, Message.unapply
  )("message_id", "text", "chat", "entities", "forward_date")

  implicit def messageToSendCodecJson: EncodeJson[MessageToSend] = EncodeJson { (m: MessageToSend) =>
    ("chat_id" := m.chatId) ->:
      ("text" := m.text) ->:
      m.replyToMessageId.map("reply_to_message_id" := _) ->?:
      m.parseMode.map("parse_mode" := _) ->?:
      m.replyMarkup.map("reply_markup" := _) ->?:
      jEmptyObject
  }

  implicit def replyMarkupCodecJson: EncodeJson[ReplyMarkup] = EncodeJson { (r: ReplyMarkup) =>
    r match {
      case f: ForceReplyMarkup => forceReplyMarkupCodecJson(f)
      case k: ReplyKeyboardMarkup => replyKeyboardMarkupCodecJson(k)
      case k: ReplyKeyboardRemoveMarkup => replyKeyboardRemoveMarkupCodecJson(k)
      case k: InlineKeyboardMarkup => inlineKeyboardMarkupCodecJson(k)
    }
  }

  implicit def forceReplyMarkupCodecJson: EncodeJson[ForceReplyMarkup] = EncodeJson { (f: ForceReplyMarkup) =>
    ("force_reply" := true) ->:
      jEmptyObject
      //("selective" := f.selective) ->:
  }

  implicit def inlineKeyboardMarkupCodecJson: EncodeJson[InlineKeyboardMarkup] = EncodeJson { (f: InlineKeyboardMarkup) =>
    ("inline_keyboard" := f.inlineKeyboard) ->:
      jEmptyObject
  }

  implicit def replyKeyboardRemoveMarkupCodecJson: EncodeJson[ReplyKeyboardRemoveMarkup] = EncodeJson {
    (r: ReplyKeyboardRemoveMarkup) =>
      ("hide_keyboard" := true) ->:
        ("selective" := r.selective) ->:
        jEmptyObject
  }

  implicit def replyKeyboardMarkupCodecJson: EncodeJson[ReplyKeyboardMarkup] = EncodeJson { (k: ReplyKeyboardMarkup) =>
    ("keyboard" := k.buttons) ->:
      ("resize_keyboard" := k.resizeKeyboard) ->:
      ("one_time_keyboard" := k.oneTimeKeyboard) ->:
      ("selective" := k.selective) ->:
      jEmptyObject
  }

  implicit def keyboardButtonCodecJson: EncodeJson[KeyboardButton] = EncodeJson { (b: KeyboardButton) =>
    ("text" := b.text) ->:
      ("request_contact" := b.requestContact) ->:
      ("request_location" := b.requestLocaton) ->:
      jEmptyObject
  }

  implicit def inlineKeyboardButtonCodecJson: EncodeJson[InlineKeyboardButton] = EncodeJson { (b: InlineKeyboardButton) =>
    ("text" := b.text) ->:
      b.url.map("url" := _) ->?:
      b.callbackData.map("callback_data" := _) ->?:
      b.switchInlineQuery.map("switch_inline_query" := _) ->?:
      b.switchInlineQueryCurrentChat.map("switch_inline_query_current_chat" := _) ->?:
      jEmptyObject
  }

  implicit def telegramUpdateCodecJson: CodecJson[TelegramUpdate] = casecodec4(
    TelegramUpdate.apply, TelegramUpdate.unapply
  )("update_id", "message", "inline_query", "callback_query")

  implicit def inlineQueryCodecJson: CodecJson[InlineQuery] = casecodec4(
    InlineQuery.apply, InlineQuery.unapply
  )("id", "from", "query", "offset")

  implicit def callbackQueryCodecJson: CodecJson[CallbackQuery] = casecodec7(
    CallbackQuery.apply, CallbackQuery.unapply
  )("id", "from", "message", "inline_message_id", "chat_instance", "data", "game_short_name")

  implicit def answerCallbackQueryCodecJson: EncodeJson[AnswerCallbackQuery] = EncodeJson { (a: AnswerCallbackQuery) =>
    ("callback_query_id" := a.callbackQueryId) ->:
    a.text.map("text" := _) ->?:
    ("show_alert" := a.showAlert) ->:
    a.url.map("url" := _) ->?:
    a.cacheTime.map("cache_time" := _) ->?:
    jEmptyObject
  }

  implicit def editMessageTextCodecJson: EncodeJson[EditMessageText] = EncodeJson { (a: EditMessageText) =>
    ("chat_id" := a.chatId) ->:
    ("message_id" := a.messageId) ->:
    ("text" := a.text) ->:
    a.parseMode.map("parse_mode" := _) ->?:
    a.replyMarkup.map("reply_markup" := _) ->?:
    jEmptyObject
  }

  implicit def editMessageReplyMarkupCodecJson: EncodeJson[EditMessageReplyMarkup] = EncodeJson { (a: EditMessageReplyMarkup) =>
    a.chatId.map("chat_id" := _) ->?:
    a.messageId.map("message_id" := _) ->?:
    a.inlineMessageId.map("inline_message_id" := _) ->?:
    a.replyMarkup.map("reply_markup" := _) ->?:
    jEmptyObject
  }

  implicit def deleteMessageCodecJson: CodecJson[DeleteMessage] = casecodec2(
    DeleteMessage.apply, DeleteMessage.unapply
  )("chat_id", "message_id")

  implicit def telegramUserCodecJson: CodecJson[TelegramUser] = casecodec6(
    TelegramUser.apply, TelegramUser.unapply
  )("id", "is_bot", "first_name", "last_name", "username", "language_code")

//  implicit def inlineQueryResultCodecJson: EncodeJson[InlineQueryResult] = EncodeJson { (r: InlineQueryResult) =>
//    r match {
//      case c: InlineQueryResultContact => inlineQueryResultContactCodecJson(c)
//    }
//  }

//  implicit def inlineQueryResultContactCodecJson: EncodeJson[InlineQueryResultContact] = EncodeJson { (c: InlineQueryResultContact) =>
//    ("id" := c.id) ->:
//      ("type" := c.resultType) ->:
//      ("phone_number" := c.phoneNumber) ->:
//      ("first_name" := c.firstName) ->:
//      jEmptyObject
//  }
//
//  implicit def answerInlineQueryCodecJson: CodecJson[AnswerInlineQuery] = casecodec3(
//    AnswerInlineQuery.apply, AnswerInlineQuery.unapply
//  )("inline_query_id", "results", "is_personal")

  implicit def inputMessageContentCodecJson: EncodeJson[InputMessageContent] = EncodeJson { (r: InputMessageContent) =>
    r match {
      case t: InputTextMessageContent => inputTextMessageContentCodecJson(t)
    }
  }

  implicit def inputTextMessageContentCodecJson: EncodeJson[InputTextMessageContent] = EncodeJson { (t: InputTextMessageContent) =>
    ("message_text" := t.messageText) ->:
      ("parse_mode" := t.parseMode) ->:
      ("disable_web_page_preview" := t.disableWebPagePreview) ->:
      jEmptyObject
  }

  implicit def chosenInlineResultCodecJson: CodecJson[ChosenInlineResult] = casecodec4(
    ChosenInlineResult.apply, ChosenInlineResult.unapply
  )("result_id", "from", "query", "inline_message_id")
}

