package com.github.rusabakumov.bots.telegram.connector

import argonaut._
import com.github.rusabakumov.bots.telegram.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.TelegramUpdate
import com.typesafe.scalalogging.{Logger => ScalaLogger}
import java.nio.file.Paths
import org.http4s.argonaut._
import org.http4s.server.blaze._
import org.slf4j.LoggerFactory
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server

/** Should be used only by TelegramConnector */
class MessageReceiverService(
    token: String,
    keystorePath: String,
    password: String,
    port: Int,
    messageHandler: TelegramMessageHandler
) {
  import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._

  private val sl4jLogger = LoggerFactory.getLogger(this.getClass)
  private val log = ScalaLogger(sl4jLogger)

  private val service = HttpService[IO] {
    case req @ POST -> Root / "receiveUpdate" / `token` =>
      req.decode[Json] { json =>
        json.as[TelegramUpdate].result match {
          case Left((errMsg, _)) =>
            log.error(s"Cannot decode update with error: $errMsg")
          case Right(update) =>
            update.message foreach messageHandler.handleMessage
        }
        Ok()
      }
  }

  private val keypath = Paths.get(keystorePath).toAbsolutePath.toString

  val server: Server[IO] = BlazeBuilder[IO]
    .withSSL(StoreInfo(keypath, password), keyManagerPassword = password)
    .mountService(service)
    .bindHttp(port, "0.0.0.0")
    .start
    .unsafeRunSync()

  def shutdown(): Unit = {
    server.shutdownNow()
  }
}
