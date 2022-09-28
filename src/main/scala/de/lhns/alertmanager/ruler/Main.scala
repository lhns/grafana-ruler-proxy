package de.lhns.alertmanager.ruler

import cats.data.OptionT
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lhns.alertmanager.ruler.repo.{AlertmanagerConfigRepoFileImpl, RulesConfigRepoFileImpl}
import de.lhns.alertmanager.ruler.route.{AlertmanagerRoutes, PrometheusRoutes}
import de.lolhens.trustmanager.TrustManagers._
import io.circe.syntax._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.{Router, Server}
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
      namespace = "rules"
      prometheusRoutesOption <- Resource.eval {
        (for {
          prometheusConf <- OptionT.fromOption[IO](config.prometheus)
          rulesConfigRepo <- OptionT.liftF {
            RulesConfigRepoFileImpl[IO](
              filePath = prometheusConf.rulePath,
              namespace = namespace
            )
          }
          prometheusRoutes <- OptionT.liftF {
            PrometheusRoutes(
              client = client,
              prometheusUrl = prometheusConf.url,
              alertmanagerConfigApiEnabled = config.alertmanager.isDefined,
              rulesConfigRepo = rulesConfigRepo,
              namespaceMappings = Map(
                prometheusConf.internalRulePath -> namespace
              ),
              warnDelay = config.warnDelayOrDefault
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
          alertmanagerRoutes <- OptionT.liftF {
            AlertmanagerRoutes(
              client = client,
              alertmanagerUrl = alertmanagerConf.url,
              alertmanagerConfigRepo = alertmanagerConfigRepo,
              warnDelay = config.warnDelayOrDefault
            )
          }
        } yield alertmanagerRoutes).value
      }
      routes = Router[IO](
        "/prometheus" -> prometheusRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO]),
        "/" -> alertmanagerRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO])
      )
      _ <- serverResource(
        host"0.0.0.0",
        config.httpPortOrDefault,
        routes.orNotFound
      )
    } yield ()

  def serverResource(host: Host, port: Port, http: HttpApp[IO]): Resource[IO, Server] =
    for {
      server <- EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(
          ErrorAction.log(
            http = http,
            messageFailureLogAction = (t, msg) => IO(logger.debug(t)(msg)),
            serviceErrorLogAction = (t, msg) => IO(logger.error(t)(msg))
          ))
        .build
    } yield server
}
