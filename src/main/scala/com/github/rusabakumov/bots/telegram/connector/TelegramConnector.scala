package com.github.rusabakumov.bots.telegram.connector

import argonaut.Argonaut._
import argonaut.Json
import com.github.rusabakumov.bots.telegram.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.{Message, MessageToSend, TelegramUpdate}
import com.github.rusabakumov.util.Logging
import java.io.File
import org.http4s.Status.ResponseClass.Successful
import org.http4s.argonaut._
import org.http4s.client._
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl._
import org.http4s.multipart.{Multipart, Part}
import org.http4s.{Request, Uri, UrlForm}
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

class TelegramConnector(botCredentials: String) extends Logging {

  import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._

  implicit private val apiClient = PooledHttp1Client()

  private val baseUri = Uri.fromString("https://api.telegram.org/" + botCredentials) match {
    case -\/(_) =>
      throw new IllegalArgumentException("Cannot construct telegram API url for configured bot credentials")
    case \/-(uri) =>
      uri
  }

  private var receiver: Option[MessageReceiverService] = None

  private def fetchResult(request: Task[Request])(implicit client: Client): Either[String, Json] = {
    val bodyTask = client.fetch(request) {
      case Successful(entity) => entity.as[Json]
      case BadRequest(entity) => entity.as[Json]
      case _ => Task.now(jNull)
    }
    val body = bodyTask.run

    body.fieldOrFalse("ok") match {
      case j: Json if j.isFalse =>
        val errMsg = body.fieldOrEmptyString("description").as[String].result.getOrElse("")
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
    val result = fetchResult(request).flatMap(_.as[Message].result.left.map(_._1))

    if (result.isLeft) {
      log.info(s"Failed to sent reply message: ${result.swap.getOrElse("")}")
    }

    result
  }

  /** This method is blocking for now! */
  def startBlockingPollingReceive(interval: Duration, messageHandler: TelegramMessageHandler) = {
    clearWebhook()
    var lastUpdateId = 0
    for (i <- 1 to 1000) {
      lastUpdateId = getUpdates(lastUpdateId + 1, messageHandler)
      Thread.sleep(interval.toMillis)
    }
  }

  /**
    * Start continuous message receiving messages for this bot. Messages are process using given message handler
    *
    * @return true if webhook was set and false if it's already been set earlier
    */
  def setReceivingWebhook(host: String, port: Int, certificate: Option[File], keystorePath: String, password: String,
                          messageHandler: TelegramMessageHandler): Boolean = {
    if (receiver.isEmpty) {
      //Regenerate each time
      val token = "j3tdh83bd63"

      val hookUrl = s"https://$host:$port/receiveUpdate/" + token

      receiver = Some(new MessageReceiverService(token, keystorePath, password, port, messageHandler))
      clearWebhook()
      setWebhook(hookUrl, certificate)

      true
    } else {
      false
    }
  }

  def stopReceivingWebhook(): Unit = {
    clearWebhook()
    receiver.foreach(_.shutdown())
    receiver = None
  }

  private def getUpdates(offset: Int, messageHandler: TelegramMessageHandler): Int = {
    val methodName = "getUpdates"

    val request = POST(baseUri / methodName, UrlForm("offset" -> offset.toString))

    fetchResult(request).flatMap(_.as[List[TelegramUpdate]].result.left.map(_._1)) match {
      case Left(errMsg) =>
        log.error(s"Failed to extract messages with error: $errMsg")
        offset

      case Right(updates) =>
        val messages = updates.flatMap(_.message)
        if (messages.nonEmpty) log.info(s"Received messages: $messages")
        messages foreach messageHandler.handleMessage

        updates.map(_.updateId) match {
          case Nil  => 0
          case list => list.max(Ordering[Int])
        }
    }
  }

  private def setWebhook(hookUrl: String, certificate: Option[File]) = {
    val methodName = "setWebhook"

    val request = certificate match {
      case Some(file) =>
        val urlPart = Part.formData("url", hookUrl)
        val filePart = Part.fileData("certificate", file)
        val multipart = Multipart(Vector(urlPart, filePart))

        POST(baseUri / methodName, multipart).map(_.replaceAllHeaders(multipart.headers))
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
    stopReceivingWebhook()
    apiClient.shutdownNow()
  }

}
