package com.github.rusabakumov.bots.telegram.connector

import akka.actor.ActorSystem
import akka.http.javadsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.github.rusabakumov.bots.telegram.handlers.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.TelegramUpdate
import com.typesafe.scalalogging.{Logger => ScalaLogger}
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import java.io.{FileInputStream, InputStream}
import java.nio.file.Paths
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

class TelegramMessageReceiverService(
    token: String,
    keystorePath: String,
    password: String,
    host: String,
    port: Int,
    messageHandler: TelegramMessageHandler
) extends ArgonautSupport {

  import com.github.rusabakumov.bots.telegram.model.TelegramModelCodecs._

  private val sl4jLogger = LoggerFactory.getLogger(this.getClass)
  private val log = ScalaLogger(sl4jLogger)

  private var server: Option[Future[ServerBinding]] = None

  implicit val system: ActorSystem = ActorSystem("telegram-bot-webhook-system")
  implicit val executionContext: ExecutionContext = system.dispatcher

  private def rejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case malformed: MalformedRequestContentRejection  =>
        log.error(s"Cannot process request due to: ${malformed.message}")
        reject(malformed)

      case any =>
        log.error(s"Cannot process request due to: any")
        reject(any)
    }
    .result()

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case th: Throwable =>
      log.error(s"Encountered error ${th.getMessage} while processing request")
      complete(StatusCodes.InternalServerError)
  }

  private val route: Route = {
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        pathPrefix("receiveUpdate") {
          path(token) {
            post {
              entity(as[TelegramUpdate]) { update =>
                log.info(s"Processing update ${update.updateId}")
                update.message.foreach(messageHandler.handleMessage)
                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    }
  }

  // No stopping is supported now
  def runService(): Unit = {
    if (server.isEmpty) {
      server = Some(
        Http()
          .newServerAt(host, port)
          .enableHttps(initSSLContext(keystorePath, password))
          .bind(route)
      )
      log.info(s"Webhook service started")
    }
  }

  private def initSSLContext(
      keystorePath: String,
      password: String
  ): HttpsConnectionContext = {
    val ks: KeyStore = KeyStore.getInstance("JKS")

    val keystore: InputStream = new FileInputStream(Paths.get(keystorePath).toAbsolutePath.toString)

    require(keystore != null, "Keystore required!")
    ks.load(keystore, password.toCharArray)

    val keyManagerFactory: KeyManagerFactory =
      KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password.toCharArray)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers,
                    tmf.getTrustManagers,
                    new SecureRandom)
    ConnectionContext.httpsServer(sslContext)
  }
}
