package com.github.rusabakumov.bots.telegram.connector

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import argonaut.Argonaut._
import argonaut.DecodeJson
import com.github.rusabakumov.bots.telegram.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._
import com.github.rusabakumov.bots.telegram.model._
import com.github.rusabakumov.util.Logging
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import java.io.File
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class TelegramConnector(botCredentials: String)
  extends Logging
  with ArgonautSupport {

  import TelegramConnector._

  implicit val system = ActorSystem("telegram-connector")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val baseUri: Uri = "https://api.telegram.org/" + botCredentials + "/"

  private def logError(methodName: String, codeOpt: Option[Int], descOpt: Option[String]) = {
    val codeMsg = codeOpt.map(c => s"(code $c)").getOrElse("")
    val msg = s"Telegram api returned error $codeMsg for method $methodName"
    val errorMsg = descOpt.map(m => s"$msg: $m").getOrElse(msg)
    log.debug(errorMsg)
    Left(errorMsg)
  }

  def sendMessage(messageToSend: MessageToSend): Future[Either[String, Message]] = {
    val methodName = "sendMessage"

    log.debug(s"Trying to send message: $messageToSend")

    val responseFuture = Marshal(messageToSend).to[RequestEntity].flatMap { entity =>
      log.debug(s"Sending entity: $entity")
      val request = HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = entity
      )

      Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Message]])
      .map {
        case TelegramAPIResult(_, Some(message), _, _) =>
          log.debug(s"Received message: $message")
          Right(message)

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err =>
          val msg = s"Failed to send message with error: ${err.getMessage}"
          log.debug(msg)
          Left(msg)
      }
  }

  def answerCallbackQuery(answer: AnswerCallbackQuery): Future[Either[String, Boolean]] = {
    val methodName = "answerCallbackQuery"

    log.debug(s"Trying to send answer: $answer")

    val responseFuture = Marshal(answer).to[RequestEntity].flatMap { entity =>
      log.debug(s"Sending entity: $entity")
      val request = HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = entity
      )

      Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Boolean]])
      .map {
        case TelegramAPIResult(_, Some(ok), _, _) =>
          log.debug(s"Answer received: $ok")
          Right(ok)

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err =>
          val msg = s"Failed to send answer with error: ${err.getMessage}"
          log.debug(msg)
          Left(msg)
      }
  }

  def editMessageText(edit: EditMessageText): Future[Either[String, Message]] = {
    val methodName = "editMessageText"

    log.debug(s"Trying to edit message: $edit")

    val responseFuture = Marshal(edit).to[RequestEntity].flatMap { entity =>
      log.debug(s"Sending entity: $entity")
      val request = HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = entity
      )

      Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Message]])
      .map {
        case TelegramAPIResult(_, Some(message), _, _) =>
          log.debug(s"Received message: $message")
          Right(message)

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err =>
          val msg = s"Failed to edit message with error: ${err.getMessage}"
          log.debug(msg)
          Left(msg)
      }
  }

  def editMessageReplyMarkup(edit: EditMessageReplyMarkup): Future[Either[String, Message]] = {
    val methodName = "editMessageReplyMarkup"

    log.debug(s"Trying to edit message markup: $edit")

    val responseFuture = Marshal(edit).to[RequestEntity].flatMap { entity =>
      log.debug(s"Sending entity: $entity")
      val request = HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = entity
      )

      Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Message]])
      .map {
        case TelegramAPIResult(_, Some(message), _, _) =>
          log.debug(s"Received message: $message")
          Right(message)

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err =>
          val msg = s"Failed to edit message with error: ${err.getMessage}"
          log.debug(msg)
          Left(msg)
      }
  }

  def deleteMessage(chatId: Long, messageId: Long) = {
    val methodName = "deleteMessage"

    log.debug(s"Trying to delete message: $messageId in $chatId")

    val params = DeleteMessage(chatId, messageId)

    val responseFuture = Marshal(params).to[RequestEntity].flatMap { entity =>
      log.debug(s"Sending entity: $entity")
      val request = HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = entity
      )

      Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Boolean]])
      .map {
        case TelegramAPIResult(_, Some(ok), _, _) =>
          log.debug(s"Message deleted: $ok")
          Right(ok)

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err =>
          val msg = s"Failed to edit message with error: ${err.getMessage}"
          log.debug(msg)
          Left(msg)
      }    
  }

  @tailrec
  final def startLongPollingReceiver(
    messageHandler: TelegramMessageHandler,
    offset: Long = 0L,
    requestTimeout: Int
  ): Unit = {
    clearWebhook()
    val methodName = "getUpdates"

    val responseFuture = Http().singleRequest(
      HttpRequest(
        HttpMethods.POST,
        baseUri + methodName,
        entity = FormData(
          "offset" -> offset.toString,
          "timeout" -> requestTimeout.toString
        ).toEntity
      )
    )

    val updatesResponse = responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[List[TelegramUpdate]]])
      .map {
        case TelegramAPIResult(_, Some(updates), _, _) =>
          val messages = updates.flatMap(_.message)
          if (messages.nonEmpty) log.info(s"Received messages: $messages")
          messages.foreach(messageHandler.handleMessage)

          val callbackQueries = updates.flatMap(_.callbackQuery)
          if (callbackQueries.nonEmpty) log.info(s"Received callback queries: $callbackQueries")
          callbackQueries.foreach(messageHandler.handleCallbackQuery)

          Right(updates.map(_.updateId) match {
            case Nil  => 0
            case list => list.max(Ordering[Long])
          })

        case TelegramAPIResult(_, None, code, description) => logError(methodName, code, description)
      }
      .recover {
        case err => Left(err.getMessage)
      }

    //Waiting request to finish. Adding 2 seconds to passed timeout for network overhead
    val result: Either[String, Long] = try {
      Await.result(updatesResponse, (requestTimeout + 2).seconds)
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        log.warn(s"Can't get updates within specified timeout. Check connection to telegram api servers")
        Right(offset)
    }

    //We have to unroll previous future separately because tailrec would'n work otherwise
    result match {
      case Right(lastOffset) => startLongPollingReceiver(messageHandler, lastOffset + 1, requestTimeout) //+1 is important
      case Left(err) => log.error(s"Error during processing updates: $err")
    }
  }

  /**
    * Start continuous message receiving for this bot. Messages are process using given message handler
    */
  def startWebhookReceiver(
      host: String,
      port: Int,
      bindHost: String,
      bingPort: Int,
      certificate: File,
      keystorePath: String,
      password: String,
      messageHandler: TelegramMessageHandler
  ): Unit = {
    //Regenerate each time
    val token = "j3tdh83bd63"

    val hookUrl = s"https://$host:$port/receiveUpdate/" + token

    clearWebhook()
    setWebhook(hookUrl, certificate)

    new WebhookReceiverService(
      token,
      keystorePath,
      password,
      bindHost,
      bingPort,
      messageHandler
    ).runService()
  }

  private def setWebhook(hookUrl: String, certificate: File) = {
    val methodName = "setWebhook"

    val urlPart = Multipart.FormData.BodyPart.Strict("url", hookUrl)
    val maxConnectionsPart =
      Multipart.FormData.BodyPart.Strict("max_connections", "1")
    val bodyPartOption = Multipart.FormData.BodyPart.fromFile(
      "certificate",
      ContentTypes.`application/octet-stream`,
      certificate)

    val multipartData =
      Multipart.FormData(List(urlPart, maxConnectionsPart, bodyPartOption): _*)

    val responseFuture = Marshal(multipartData).to[RequestEntity].flatMap {
      entity =>
        val request = HttpRequest(
          HttpMethods.POST,
          baseUri + methodName,
          entity = entity
        )

        Http().singleRequest(request)
    }

    responseFuture
      .flatMap(Unmarshal(_).to[TelegramAPIResult[Unit]])
      .map(Right(_))
      .recover {
        case err =>
          Left(s"Failed to set webhook: ${err.getMessage}")
      }
  }

  private def clearWebhook(): Unit = {
    val methodName = "deleteWebhook"

    val responseFuture = Http().singleRequest(
      HttpRequest(
        HttpMethods.GET,
        baseUri + methodName
      )
    )

    responseFuture.recover {
      case err => Left(s"Failed to send message with error: ${err.getMessage}")
    }
  }
}

object TelegramConnector {
  case class TelegramAPIResult[T](ok: Boolean, result: Option[T], error_code: Option[Int], description: Option[String])

  implicit def telegramAPIResultDecodeJson[T](implicit ev: DecodeJson[T]): DecodeJson[TelegramAPIResult[T]] =
    jdecode4L(TelegramAPIResult.apply[T])("ok", "result", "error_code", "description")

  implicit def getUpdatesEncodeJson: DecodeJson[TelegramAPIResult[List[TelegramUpdate]]] =
    telegramAPIResultDecodeJson[List[TelegramUpdate]]

  implicit def sendMessageEncodeJson: DecodeJson[TelegramAPIResult[Message]] =
    telegramAPIResultDecodeJson[Message]
}
