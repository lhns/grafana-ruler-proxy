package de.lhns.alertmanager.ruler

import cats.data.OptionT
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lhns.alertmanager.ruler.repo.{AlertmanagerConfigRepoFileImpl, RulesConfigRepoFileImpl}
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
      prometheusRoutesOption <- Resource.eval {
        (for {
          prometheusConf <- OptionT.fromOption[IO](config.prometheus)
          rulesConfigRepo <- OptionT.liftF {
            RulesConfigRepoFileImpl[IO](
              filePath = prometheusConf.rulePath,
              namespace = prometheusConf.internalRulePath
            )
          }
          prometheusRoutes <- OptionT.liftF {
            PrometheusRoutes(
              client = client,
              prometheusUrl = prometheusConf.url,
              alertmanagerConfigApiEnabled = config.alertmanager.isDefined,
              rulesConfigRepo = rulesConfigRepo
            )
          }
        } yield
          prometheusRoutes)
          .value
      }
      alertmanagerRoutesOption <- Resource.eval {
        (for {
          alertmanagerConf <- OptionT.fromOption[IO](config.alertmanager)
          alertmanagerConfigRepo <- OptionT.liftF {
            AlertmanagerConfigRepoFileImpl[IO](
              filePath = alertmanagerConf.configPath
            )
          }
          alertmanagerRoutes = new AlertmanagerRoutes(
            client = client,
            alertmanagerUrl = alertmanagerConf.url,
            alertmanagerConfigRepo = alertmanagerConfigRepo
          )
        } yield alertmanagerRoutes).value
      }
      routes = Router[IO](
        "/prometheus" -> prometheusRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO]),
        "/" -> alertmanagerRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO])
      )
      _ <- EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(config.httpPortOrDefault)
        .withHttpApp(routes.orNotFound)
        .build
    } yield ()
}
