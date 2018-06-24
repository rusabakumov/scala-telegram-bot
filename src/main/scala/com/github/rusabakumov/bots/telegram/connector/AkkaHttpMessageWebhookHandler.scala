package com.github.rusabakumov.bots.telegram.connector

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.github.rusabakumov.bots.telegram.TelegramMessageHandler
import com.github.rusabakumov.bots.telegram.model.TelegramUpdate
import com.typesafe.scalalogging.{Logger => ScalaLogger}
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.slf4j.LoggerFactory
import scala.concurrent.Future

class AkkaHttpMessageWebhookHandler(
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

  implicit val system = ActorSystem("telegram-bot-webhook-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  private val route: Route = {
    path(s"receiveUpdate/$token") {
      post {
        entity(as[TelegramUpdate]) { update =>
          log.info(s"Processing update ${update.updateId}")
          update.message.foreach(messageHandler.handleMessage)
          complete(StatusCodes.OK)
        }
      }
    }
  }

  def runService(): Unit = {
    if (server.isEmpty) {
      server = Some(
        Http().bindAndHandle(
          route,
          "0.0.0.0",
          port,
          connectionContext = initSSLContext(keystorePath, password)
        )
      )
    }
  }

  def stopServer(): Unit = {
    //TODO: work with server in a more thread-safe way
    server.foreach { bindingFuture =>
      bindingFuture.flatMap(_.unbind())
      server = None
    }
  }

  private def initSSLContext(
      keystorePath: String,
      password: String
  ): HttpsConnectionContext = {
    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream =
      getClass.getClassLoader.getResourceAsStream(keystorePath)

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
    ConnectionContext.https(sslContext)
  }
}
