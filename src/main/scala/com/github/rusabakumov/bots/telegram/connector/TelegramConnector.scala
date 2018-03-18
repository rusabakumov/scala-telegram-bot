package com.github.rusabakumov.bots.telegram.connector

import argonaut.Argonaut._
import argonaut._
import cats.effect.IO
import com.github.rusabakumov.bots.telegram.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend, TelegramUpdate}
import com.github.rusabakumov.util.Logging
import java.io.File
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.argonaut._
import org.http4s.client._
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.multipart._
import scala.concurrent.duration.Duration

class TelegramConnector(botCredentials: String)
    extends Http4sClientDsl[IO]
    with Logging {

  import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._

  implicit private val apiClient = Http1Client[IO]().unsafeRunSync()

  private val baseUri: Uri =
    Uri.fromString("https://api.telegram.org/" + botCredentials) match {
      case Left(_) =>
        throw new IllegalArgumentException(
          "Cannot construct telegram API url for configured bot credentials")
      case Right(uri) =>
        uri
    }

  private def fetchResult(request: IO[Request[IO]])(
      implicit client: Client[IO]): Either[String, Json] = {
    val bodyTask = client.fetch(request) {
      case Successful(entity) => entity.as[Json]
      case BadRequest(entity) => entity.as[Json]
      case _                  => IO { jNull }
    }
    val body = bodyTask.unsafeRunSync()

    body.fieldOrFalse("ok") match {
      case j: Json if j.isFalse =>
        val errMsg =
          body.fieldOrEmptyString("description").as[String].result.getOrElse("")
        Left(errMsg)
      case j: Json if j.isTrue =>
        Right(body.fieldOrNull("result"))
    }
  }

  def sendMessage(message: MessageToSend): Either[String, Message] = {
    val methodName = "sendMessage"

    val messageJson = message.asJson
    log.debug(s"Trying to send message: $messageJson")

    val request = POST(baseUri / methodName, messageJson)
    val result =
      fetchResult(request).flatMap(_.as[Message].result.left.map(_._1))

    if (result.isLeft) {
      log.info(s"Failed to sent reply message: ${result.swap.getOrElse("")}")
    }

    result
  }

  /** This method is blocking for now! */
  def startBlockingPollingReceive(interval: Duration,
                                  messageHandler: TelegramMessageHandler) = {
    clearWebhook()
    var lastUpdateId = 0L
    for (i <- 1 to 1000) {
      lastUpdateId = getUpdates(lastUpdateId + 1, messageHandler)
      Thread.sleep(interval.toMillis)
    }
  }

  /**
    * Start continuous message receiving for this bot. Messages are process using given message handler
    */
  def startMessageReceiverServer(
      host: String,
      port: Int,
      certificate: Option[File],
      keystorePath: String,
      password: String,
      messageHandler: TelegramMessageHandler
  ): Unit = {
    //Regenerate each time
    val token = "j3tdh83bd63"

    val hookUrl = s"https://$host:$port/receiveUpdate/" + token

    clearWebhook()
    setWebhook(hookUrl, certificate)

    new MessageReceiverServer(
      token,
      keystorePath,
      password,
      port,
      messageHandler)
  }

  private def getUpdates(offset: Long,
                         messageHandler: TelegramMessageHandler): Long = {
    val methodName = "getUpdates"
    val request =
      POST(baseUri / methodName, UrlForm("offset" -> offset.toString))

    fetchResult(request).flatMap(
      _.as[List[TelegramUpdate]].result.left.map(_._1)) match {
      case Left(errMsg) =>
        log.error(s"Failed to extract messages with error: $errMsg")
        offset

      case Right(updates) =>
        val messages = updates.flatMap(_.message)
        if (messages.nonEmpty) log.info(s"Received messages: $messages")
        messages foreach messageHandler.handleMessage

        updates.map(_.updateId) match {
          case Nil  => 0
          case list => list.max(Ordering[Long])
        }
    }
  }

  private def setWebhook(hookUrl: String, certificate: Option[File]) = {
    val methodName = "setWebhook"

    val request: IO[Request[IO]] = certificate match {
      case Some(file) =>
        val urlPart = Part.formData[IO]("url", hookUrl)
        val filePart = Part.fileData[IO]("certificate", file)
        val multipart = Multipart[IO](Vector(urlPart, filePart))

        val uri = baseUri / methodName

        POST(uri, multipart).map(_.replaceAllHeaders(multipart.headers))
      case None =>
        POST(baseUri / methodName, UrlForm("url" -> hookUrl))
    }

    val result = fetchResult(request)

    if (result.isLeft) {
      log.error(s"Failed to set webhook: ${result.swap.getOrElse("")}")
    }
  }

  private def clearWebhook() = {
    val methodName = "setWebhook"

    val request = POST(baseUri / methodName, UrlForm("url" -> ""))
    val result = fetchResult(request)

    if (result.isLeft) {
      log.error(s"Failed to clear webhook: ${result.swap.getOrElse("")}")
    }
  }

  def shutdown(): Unit = {
    apiClient.shutdownNow()
  }
}
