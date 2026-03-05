package de.lhns.alertmanager.ruler

import cats.effect.IO
import cats.effect.std.Env
import cats.effect.unsafe.implicits.global
import munit.FunSuite

class ConfigSpec extends FunSuite {
  test("fromEnv decodes full config") {
    val configJson =
      """
        |{
        |  "httpPort": 9090,
        |  "prometheus": {
        |    "url": "http://prometheus:9090",
        |    "rulesUrl": "http://vmalert:8880",
        |    "rulePath": "/rules.yml",
        |    "internalRulePath": "/config/rules.yml",
        |    "namespace": "prometheus"
        |  },
        |  "alertmanager": {
        |    "url": "http://alertmanager:9093",
        |    "configPath": "/alertmanager.yml"
        |  },
        |  "warnDelay": "15 seconds",
        |  "debug": true
        |}
        |""".stripMargin

    given Env[IO] with {
      override def get(name: String): IO[Option[String]] = IO.pure(Option.when(name == "CONFIG")(configJson))
      override def entries: IO[scala.collection.immutable.Iterable[(String, String)]] = IO.pure(List.empty)
    }

    val config = Config.fromEnv[IO]("CONFIG").unsafeRunSync()

    assertEquals(config.httpPort.map(_.value), Some(9090))
    assertEquals(config.warnDelayOrDefault.toSeconds, 15L)
    assertEquals(config.debugOrDefault, true)
    assertEquals(config.prometheus.map(_.url.renderString), Some("http://prometheus:9090"))
    assertEquals(config.prometheus.flatMap(_.rulesUrl).map(_.renderString), Some("http://vmalert:8880"))
    assertEquals(config.prometheus.map(_.namespaceOrDefault), Some("prometheus"))
    assertEquals(config.alertmanager.map(_.url.renderString), Some("http://alertmanager:9093"))
  }

  test("fromEnv applies defaults when optional fields are absent") {
    val configJson =
      """
        |{
        |  "prometheus": {
        |    "url": "http://prometheus:9090",
        |    "rulePath": "/rules.yml",
        |    "internalRulePath": "/config/rules.yml"
        |  }
        |}
        |""".stripMargin

    given Env[IO] with {
      override def get(name: String): IO[Option[String]] = IO.pure(Option.when(name == "CONFIG")(configJson))
      override def entries: IO[scala.collection.immutable.Iterable[(String, String)]] = IO.pure(List.empty)
    }

    val config = Config.fromEnv[IO]("CONFIG").unsafeRunSync()

    assertEquals(config.httpPortOrDefault.value, 8080)
    assertEquals(config.warnDelayOrDefault.toSeconds, 10L)
    assertEquals(config.debugOrDefault, false)
    assertEquals(config.prometheus.map(_.rulesUrlOrDefault.renderString), Some("http://prometheus:9090"))
    assertEquals(config.prometheus.map(_.namespaceOrDefault), Some("/config/rules.yml"))
  }

  test("fromEnv fails when environment variable is missing") {
    given Env[IO] with {
      override def get(name: String): IO[Option[String]] = IO.pure(None)
      override def entries: IO[scala.collection.immutable.Iterable[(String, String)]] = IO.pure(List.empty)
    }

    val result = Config.fromEnv[IO]("CONFIG").attempt.unsafeRunSync()

    assert(result.isLeft)
    val message = result.swap.toOption.map(_.getMessage).getOrElse("")
    assert(message.contains("Missing environment variable: CONFIG"))
  }
}
