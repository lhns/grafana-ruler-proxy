package de.lhns.alertmanager.ruler

import cats.data.EitherT
import cats.effect.Concurrent
import cats.syntax.functor._
import io.circe.yaml.syntax._
import org.http4s.headers.`Content-Type`
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, MalformedMessageBodyFailure, MediaType}

package object route {
  implicit def yamlDecoder[F[_] : Concurrent]: EntityDecoder[F, YamlSyntax] =
    EntityDecoder.decodeBy[F, YamlSyntax](MediaType.text.yaml) { media =>
      EitherT(media.as[String].map(io.circe.yaml.parser.parse))
        .leftMap[DecodeFailure](failure => MalformedMessageBodyFailure("Invalid YAML", Some(failure)))
        .map(_.asYaml)
    }

  implicit def yamlEncoder[F[_] : Concurrent]: EntityEncoder[F, YamlSyntax] =
    EntityEncoder.stringEncoder[F]
      .withContentType(`Content-Type`(MediaType.text.yaml))
      .contramap(_.spaces2)
}
