package de.lhns.alertmanager.ruler

import com.comcast.ip4s._
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri

import java.nio.file.{Path, Paths}

case class Config(
                   httpPort: Option[Port],
                   prometheus: Option[Config.PrometheusConf],
                   alertmanager: Option[Config.AlertmanagerConf]
                 ) {
  val httpPortOrDefault: Port = httpPort.getOrElse(port"8080")
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

  implicit val codec: Codec[Config] = deriveCodec
}
