package de.lhns.alertmanager.ruler

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s._
import com.github.markusbernhardt.proxy.ProxySearch
import de.lolhens.trustmanager.TrustManagers._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.ProxySelector
import scala.util.chaining._

object Main extends IOApp {
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

    applicationResource(config).use(_ => IO.never).as(ExitCode.Success)
  }

  def applicationResource(config: Config): Resource[IO, Unit] = {
    for {
      client <- JdkHttpClient.simple[IO]
      rulesConfig <- Resource.eval(RulesConfigFile[IO](config.rulePath))
      _ <- EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(config.httpPortOrDefault)
        .withHttpApp(Routes(
          client = client,
          alertmanagerUrl = config.alertmanagerUrl,
          rulesConfig = rulesConfig
        ).orNotFound)
        .build
    } yield ()
  }
}
