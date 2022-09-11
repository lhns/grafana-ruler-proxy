package de.lhns.alertmanager.ruler

import cats.data.OptionT
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lhns.alertmanager.ruler.route.{AlertmanagerRoutes, PrometheusRoutes}
import de.lolhens.trustmanager.TrustManagers._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.Router
import org.log4s.getLogger

import java.net.ProxySelector
import scala.util.chaining._

object Main extends IOApp {
  private val logger = getLogger

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    setDefaultTrustManager(jreTrustManagerWithEnvVar)

    val config =
      Option(System.getenv("CONFIG"))
        .map(io.circe.config.parser.decode[Config](_).toTry.get)
        .getOrElse(throw new IllegalArgumentException("Missing variable: CONFIG"))

    logger.info(config.asJson.spaces2)

    applicationResource(config).use(_ => IO.never).as(ExitCode.Success)
  }

  def applicationResource(config: Config): Resource[IO, Unit] =
    for {
      client <- JdkHttpClient.simple[IO]
      rulesConfig <- Resource.eval {
        RulesConfigFile[IO](
          filePath = config.rulePath,
          namespace = config.internalRulePath
        )
      }
      prometheusRoutes <- Resource.eval {
        OptionT.fromOption[IO](config.prometheusUrl)
          .semiflatMap { prometheusUrl =>
            PrometheusRoutes(
              client = client,
              prometheusUrl = prometheusUrl,
              alertmanagerConfigApiEnabled = config.alertmanagerUrl.isDefined,
              rulesConfig = rulesConfig
            )
          }
          .getOrElse(HttpRoutes.empty[IO])
      }
      alertmanagerRoutes = config.alertmanagerUrl
        .map { alertmanagerUrl =>
          AlertmanagerRoutes(
            client = client,
            alertmanagerUrl = alertmanagerUrl
          )
        }
        .getOrElse(HttpRoutes.empty[IO])
      routes = Router[IO](
        "/prometheus" -> prometheusRoutes,
        "/alertmanager" -> alertmanagerRoutes
      )
      _ <- EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(config.httpPortOrDefault)
        .withHttpApp(routes.orNotFound)
        .build
    } yield ()
}
