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
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend, TelegramUpdate}
import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._
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

  def sendMessage(messageToSend: MessageToSend): Future[Either[String, Message]] = {
    val methodName = "sendMessage"

    log.debug(s"Trying to send message: $messageToSend")

    val responseFuture = Marshal(messageToSend).to[RequestEntity].flatMap { entity =>
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
        case TelegramAPIResult(_, Some(message), _) =>
          Right(message)

        case TelegramAPIResult(_, None, code) =>
          Left(s"Telegram api returned error code $code for method $methodName")
      }
      .recover {
        case err =>
          Left(s"Failed to send message with error: ${err.getMessage}")
      }
  }

  @tailrec
  final def startLongPollingReceiver(
    messageHandler: TelegramMessageHandler,
    offset: Long = 0L,
    requestTimeout: Int = 3
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
        case TelegramAPIResult(_, Some(updates), _) =>
          val messages = updates.flatMap(_.message)
          if (messages.nonEmpty) log.info(s"Received messages: $messages")
          messages.foreach(messageHandler.handleMessage)

          Right(updates.map(_.updateId) match {
            case Nil  => 0
            case list => list.max(Ordering[Long])
          })

        case TelegramAPIResult(_, None, code) =>
          Left(s"Telegram api returned error code $code for method $methodName")
      }
      .recover {
        case err => Left(err.getMessage)
      }

    //Waiting request to finish. Adding 2 seconds to passed timeout for network overhead
    val result: Either[String, Long] = try {
      Await.result(updatesResponse, (requestTimeout + 2).seconds)
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        Left(s"Can't get updates within specified timeout. Check connection to telegram api servers")
    }

    //We have to unroll previous future separately because tailrec would'n work otherwise
    result match {
      case Right(lastOffset) => startLongPollingReceiver(messageHandler, lastOffset + 1) //+1 is important
      case Left(err) => log.error(s"Error during processing updates: $err")
    }
  }

  /**
    * Start continuous message receiving for this bot. Messages are process using given message handler
    */
  def startWebhookReceiver(
      host: String,
      port: Int,
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

    new AkkaHttpMessageWebhookHandler(
      token,
      keystorePath,
      password,
      host,
      port,
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
    val methodName = "deletWebhook"

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

//  def shutdown(): Unit = {
//    apiClient.shutdownNow()
//  }
}

object TelegramConnector {
  case class TelegramAPIResult[T](ok: Boolean, result: Option[T], error_code: Option[Int])

  implicit def telegramAPIResultDecodeJson[T](implicit ev: DecodeJson[T]): DecodeJson[TelegramAPIResult[T]] =
    jdecode3L(TelegramAPIResult.apply[T])("ok", "result", "error_code")

  implicit def getUpdatesEncodeJson: DecodeJson[TelegramAPIResult[List[TelegramUpdate]]] =
    telegramAPIResultDecodeJson[List[TelegramUpdate]]

  implicit def sendMessageEncodeJson: DecodeJson[TelegramAPIResult[Message]] =
    telegramAPIResultDecodeJson[Message]
}
