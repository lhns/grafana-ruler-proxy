package de.lhns.alertmanager.ruler

import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri

import java.nio.file.{Path, Paths}

case class Config(
                   alertmanagerUrl: Uri,
                   rulePath: Path
                 )

object Config {
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
