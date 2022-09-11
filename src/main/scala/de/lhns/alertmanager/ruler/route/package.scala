package de.lhns.alertmanager.ruler

import cats.data.EitherT
import cats.effect.{Concurrent, IO}
import cats.syntax.functor._
import de.lolhens.http4s.proxy.Http4sProxy._
import io.circe.yaml.syntax._
import org.http4s.headers.`Content-Type`
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, HttpVersion, MalformedMessageBodyFailure, MediaType, Request, Uri}
import org.log4s.Logger

import scala.concurrent.duration._

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

  def warnSlowResponse[A](io: IO[A], logger: Logger, uri: Uri): IO[A] =
    for {
      fiber <- IO {
        logger.warn(s"request to $uri is taking longer than expected")
      }.delayBy(10.seconds).start
      result <- io
      _ <- fiber.cancel
    } yield result

  def changeDestination(request: Request[IO], destination: Uri): Request[IO] =
    request
      .withHttpVersion(HttpVersion.`HTTP/1.1`)
      .withDestination(
        request.uri
          .withSchemeAndAuthority(destination)
          .withPath((
            if (request.pathInfo.isEmpty) destination.path
            else destination.path.concat(request.pathInfo)
            ).toAbsolute)
      )
}
