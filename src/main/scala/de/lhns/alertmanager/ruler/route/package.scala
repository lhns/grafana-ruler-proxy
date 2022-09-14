package de.lhns.alertmanager.ruler

import cats.Monad
import cats.data.EitherT
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.effect.{Async, Concurrent, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import de.lolhens.http4s.proxy.Http4sProxy._
import io.circe.yaml.syntax._
import org.http4s.headers.`Content-Type`
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, HttpApp, HttpVersion, MalformedMessageBodyFailure, MediaType, Method, Request, Uri}
import org.log4s.getLogger

import scala.concurrent.duration._

package object route {
  private val logger = getLogger

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

  implicit class HttpAppOps[F[_]](httpApp: HttpApp[F]) {
    def warnSlowResponse(implicit F: Async[F]): HttpApp[F] = {
      HttpApp[F] { request =>
        for {
          fiber <- Async[F].delay {
            logger.warn(s"request to ${request.uri} is taking longer than expected")
          }.delayBy(10.seconds).start
          response <- httpApp(request)
          _ <- fiber.cancel
        } yield response
      }
    }

    def proxyTo(baseUrl: Uri)(implicit F: Monad[F]): HttpApp[F] = HttpApp[F] { request =>
      httpApp(
        request
          .withHttpVersion(HttpVersion.`HTTP/1.1`)
          .withDestination(
            request.uri
              .withSchemeAndAuthority(baseUrl)
              .withPath((
                if (request.pathInfo.isEmpty) baseUrl.path
                else baseUrl.path.concat(request.pathInfo)
                ).toAbsolute)
          )
      )
    }
  }

  val reloadRequest: Request[IO] = Request[IO](
    method = Method.POST,
    uri = Uri() / "-" / "reload"
  )
}
