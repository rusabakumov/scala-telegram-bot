package com.github.rusabakumov.bots.telegram.model

import argonaut.Argonaut._
import argonaut.{CodecJson, EncodeJson}

case class MessageEntity(entityType: String, offset: Int, length: Int)
case class Chat(id: Long, chatType: String, title: Option[String], username: Option[String])

case class Message(
    messageId: Long,
    text: Option[String],
    chat: Chat,
    entities: Option[List[MessageEntity]]
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

case class TelegramUpdate(updateId: Long, message: Option[Message])

object TelegramModelCodecs {

  implicit def chatCodecJson: CodecJson[Chat] = casecodec4(
    Chat.apply, Chat.unapply
  )("id", "type", "title", "username")

  implicit def messageEntityCodecJson: CodecJson[MessageEntity] = casecodec3(
    MessageEntity.apply, MessageEntity.unapply
  )("type", "offset", "length")

  implicit def messageCodecJson: CodecJson[Message] = casecodec4(
    Message.apply, Message.unapply
  )("message_id", "text", "chat", "entities")

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
    ("selective" := f.selective) ->:
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

  implicit def telegramUpdateCodecJson: CodecJson[TelegramUpdate] = casecodec2(
    TelegramUpdate.apply, TelegramUpdate.unapply
  )("update_id", "message")

}
