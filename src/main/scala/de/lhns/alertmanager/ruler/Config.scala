package de.lhns.alertmanager.ruler

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.std.Env
import com.comcast.ip4s._
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri

import java.nio.file.{Path, Paths}
import scala.concurrent.duration._

case class Config(
                   httpPort: Option[Port],
                   prometheus: Option[Config.PrometheusConf],
                   alertmanager: Option[Config.AlertmanagerConf],
                   warnDelay: Option[FiniteDuration]
                 ) {
  val httpPortOrDefault: Port = httpPort.getOrElse(port"8080")

  val warnDelayOrDefault: FiniteDuration = warnDelay.getOrElse(10.seconds)
}

object Config {
  case class PrometheusConf(
                             url: Uri,
                             rulePath: Path,
                             internalRulePath: String
                           )

  object PrometheusConf {
    implicit val codec: Codec[PrometheusConf] = deriveCodec
  }

  case class AlertmanagerConf(
                               url: Uri,
                               configPath: Path
                             )

  object AlertmanagerConf {
    implicit val codec: Codec[AlertmanagerConf] = deriveCodec
  }

  private implicit val portCodec: Codec[Port] = Codec.from(
    Decoder.decodeInt.map(port => Port.fromInt(port).getOrElse(throw new IllegalArgumentException(s"invalid port: $port"))),
    Encoder.encodeInt.contramap(_.value)
  )

  private implicit val pathCodec: Codec[Path] = Codec.from(
    Decoder.decodeString.map(Paths.get(_)),
    Encoder.encodeString.contramap(_.toString)
  )

  private implicit val uriCodec: Codec[Uri] = Codec.from(
    Decoder.decodeString.map(Uri.unsafeFromString),
    Encoder.encodeString.contramap(_.renderString)
  )

  private implicit val finiteDurationCodec: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeString.map(Duration(_) match {
      case finiteDuration: FiniteDuration => finiteDuration
    }),
    Encoder.encodeString.contramap(_.toString)
  )

  implicit val codec: Codec[Config] = deriveCodec

  def fromEnv[F[_] : Sync](env: Env[F]): F[Config] =
    OptionT(env.get("CONFIG"))
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .subflatMap(io.circe.config.parser.decode[Config](_))
      .rethrowT
}
