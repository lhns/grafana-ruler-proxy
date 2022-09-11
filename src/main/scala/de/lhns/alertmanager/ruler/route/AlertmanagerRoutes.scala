package de.lhns.alertmanager.ruler.route

import cats.effect.IO
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import de.lhns.alertmanager.ruler.repo.AlertmanagerConfigRepo
import io.circe.syntax._
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.log4s.getLogger

class AlertmanagerRoutes(
                          client: Client[IO],
                          alertmanagerUrl: Uri,
                          alertmanagerConfigRepo: AlertmanagerConfigRepo[IO]
                        ) {
  private val logger = getLogger

  private val httpApp = client.toHttpApp

  def proxyRequest(request: Request[IO]): IO[Response[IO]] = {
    val newRequest = changeDestination(request, alertmanagerUrl)
    warnSlowResponse(
      httpApp(newRequest),
      logger,
      newRequest.uri
    )
  }

  def toRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "v1" / "alerts" =>
      alertmanagerConfigRepo.getConfig
        .map(_.asJson.asYaml)
        .flatMap(Ok(_))

    case request@POST -> Root / "api" / "v1" / "alerts" =>
      request.as[YamlSyntax].flatMap { yaml =>
        val config = yaml.tree.as[AlertmanagerConfig].toTry.get
        alertmanagerConfigRepo.setConfig(config) >>
          Created()
      }

    case DELETE -> Root / "api" / "v1" / "alerts" =>
      alertmanagerConfigRepo.deleteConfig >>
        Ok()

    case request@_ -> "alertmanager" /: pathInfo =>
      proxyRequest(request.withPathInfo(pathInfo)).map { response =>
        logger.debug(s"${request.method} ${request.uri.path} -> ${response.status.code}")
        response
      }
  }
}
