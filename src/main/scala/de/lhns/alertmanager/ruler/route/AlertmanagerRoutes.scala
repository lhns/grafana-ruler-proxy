package de.lhns.alertmanager.ruler.route

import cats.effect.IO
import de.lolhens.http4s.proxy.Http4sProxy._
import fs2.Chunk
import org.http4s.client.Client
import org.http4s.{HttpRoutes, HttpVersion, Request, Response, Uri}
import org.log4s.getLogger

import java.nio.charset.StandardCharsets

object AlertmanagerRoutes {
  private val logger = getLogger

  def apply(
             client: Client[IO],
             alertmanagerUrl: Uri
           ): HttpRoutes[IO] = {
    val httpApp = client.toHttpApp

    def proxyRequest(request: Request[IO]): IO[Response[IO]] = httpApp(
      request
        .withHttpVersion(HttpVersion.`HTTP/1.1`)
        .withDestination(
          request.uri
            .withSchemeAndAuthority(alertmanagerUrl)
            .withPath((
              if (request.pathInfo.isEmpty) alertmanagerUrl.path
              else alertmanagerUrl.path.concat(request.pathInfo)
              ).toAbsolute)
        )
    )

    HttpRoutes.of[IO] {
      case request =>
        proxyRequest(request)
          .flatMap { response =>
            logger.info(s"${request.method} ${request.pathInfo} -> ${response.status.code}")
            if (logger.isDebugEnabled) {
              response.as[Chunk[Byte]].map { chunk =>
                logger.debug(new String(chunk.toArray, StandardCharsets.UTF_8))
                response.withBodyStream(fs2.Stream.chunk(chunk))
              }
            } else IO {
              response
            }
          }
    }
  }
}
