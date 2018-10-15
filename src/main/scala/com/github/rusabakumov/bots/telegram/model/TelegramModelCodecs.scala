package com.github.rusabakumov.bots.telegram.model

import argonaut.Argonaut.{casecodec1, casecodec3, casecodec4, jEmptyObject}
import argonaut.{CodecJson, EncodeJson}
import argonaut._, Argonaut._

object TelegramModelCodecs {

  implicit def chatCodecJson: CodecJson[Chat] = casecodec4(
    Chat.apply, Chat.unapply
  )("id", "type", "title", "username")

  implicit def messageEntityCodecJson: CodecJson[MessageEntity] = casecodec3(
    MessageEntity.apply, MessageEntity.unapply
  )("type", "offset", "length")

//  implicit def messageCodecJson: CodecJson[Message] = casecodec6(
//    Message.apply, Message.unapply
//  )("message_id", "text", "chat", "entities", "reply_to_message", "forward_date")

  implicit def messageDecodeJson: DecodeJson[Message] =
    DecodeJson(msg => for {
      messageId <- (msg --\ "message_id").as[Long]
      rawText <- (msg --\ "text").as[Option[String]]
      chat <- (msg --\ "chat").as[Chat]
      entities <- (msg --\ "entities").as[Option[List[MessageEntity]]]
      replyToMessage <- (msg --\ "reply_to_message").as[Option[Message]]
      forwardDate <- (msg --\ "forward_date").as[Option[Int]]
    } yield Message(messageId, rawText, chat, entities, replyToMessage, forwardDate))

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
    }
  }

  implicit def forceReplyMarkupCodecJson: EncodeJson[ForceReplyMarkup] = EncodeJson { (f: ForceReplyMarkup) =>
    ("force_reply" := true) ->:
      jEmptyObject
      //("selective" := f.selective) ->:
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

  implicit def telegramUpdateCodecJson: DecodeJson[TelegramUpdate] =
    DecodeJson(msg => for {
      updateId <- (msg --\ "update_id").as[Long]
      message <- (msg --\ "message").as[Option[Message]]
      inlineQuery <- (msg --\ "inline_query").as[Option[InlineQuery]]
    } yield TelegramUpdate(updateId, message, inlineQuery))

  implicit def inlineQueryCodecJson: CodecJson[InlineQuery] = casecodec4(
    InlineQuery.apply, InlineQuery.unapply
  )("id", "usser", "query", "offset")

  implicit def telegramUserCodecJson: CodecJson[TelegramUser] = casecodec1(
    TelegramUser.apply, TelegramUser.unapply
  )("id")

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

