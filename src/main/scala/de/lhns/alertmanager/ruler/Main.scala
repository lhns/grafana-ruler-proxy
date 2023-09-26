package de.lhns.alertmanager.ruler

import cats.data.OptionT
import cats.effect._
import cats.effect.std.Env
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lhns.alertmanager.ruler.repo.{AlertmanagerConfigRepoFileImpl, RulesConfigRepoFileImpl}
import de.lhns.alertmanager.ruler.route.{AlertmanagerRoutes, PrometheusRoutes}
import de.lhns.trustmanager.TrustManagers._
import io.circe.syntax._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.middleware.{ErrorAction, ErrorHandling}
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes}
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

import java.net.ProxySelector
import scala.concurrent.duration._
import scala.util.chaining._

object Main extends IOApp {
  private implicit val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    setDefaultTrustManager(jreTrustManagerWithEnvVar)

    applicationResource.use(_ => IO.never)
  }

  def applicationResource: Resource[IO, Unit] =
    for {
      config <- Resource.eval(Config.fromEnv(Env.make[IO]))
      _ = logger.info(s"CONFIG: ${config.asJson.spaces2}")
      client <- Resource.eval(JdkHttpClient.simple[IO])
      prometheusRoutesOption <- Resource.eval {
        (for {
          prometheusConf <- OptionT.fromOption[IO](config.prometheus)
          rulesConfigRepo <- OptionT.liftF {
            RulesConfigRepoFileImpl[IO](
              filePath = prometheusConf.rulePath,
              namespace = prometheusConf.namespaceOrDefault
            )
          }
          prometheusRoutes <- OptionT.liftF {
            PrometheusRoutes(
              client = client,
              prometheusUrl = prometheusConf.url,
              rulesUrl = prometheusConf.rulesUrlOrDefault,
              alertmanagerConfigApiEnabled = config.alertmanager.isDefined,
              rulesConfigRepo = rulesConfigRepo,
              namespaceMappings = Map(
                prometheusConf.internalRulePath -> prometheusConf.namespaceOrDefault
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
      prometheusRoutes = prometheusRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO])
      alertmanagerRoutes = alertmanagerRoutesOption.map(_.toRoutes).getOrElse(HttpRoutes.empty[IO])
      _ <- serverResource[IO](
        SocketAddress(host"0.0.0.0", config.httpPortOrDefault),
        (alertmanagerRoutes <+> prometheusRoutes).orNotFound
      )
    } yield ()

  def serverResource[F[_] : Async : Logger](
                                             socketAddress: SocketAddress[Host],
                                             http: HttpApp[F]
                                           ): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(socketAddress.host)
      .withPort(socketAddress.port)
      .withHttpApp(
        ErrorHandling.Recover.total(
          ErrorAction.log(
            http = http,
            messageFailureLogAction = (t, msg) => Logger[F].debug(t)(msg),
            serviceErrorLogAction = (t, msg) => Logger[F].error(t)(msg)
          )))
      .withShutdownTimeout(1.second)
      .build
}
