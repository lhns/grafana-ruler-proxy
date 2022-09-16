package de.lhns.alertmanager.ruler.route

import cats.effect.IO
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import de.lhns.alertmanager.ruler.repo.AlertmanagerConfigRepo
import io.circe.syntax._
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Uri}
import org.log4s.getLogger

import scala.concurrent.duration._

class AlertmanagerRoutes private(
                                  client: Client[IO],
                                  alertmanagerUrl: Uri,
                                  alertmanagerConfigRepo: AlertmanagerConfigRepo[IO],
                                  warnDelay: FiniteDuration
                                ) {
  private val logger = getLogger

  private val httpApp = client.toHttpApp.warnSlowResponse(warnDelay).proxyTo(alertmanagerUrl)

  def reloadRules: IO[Unit] =
    httpApp(reloadRequest).start.void

  def toRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "v1" / "alerts" =>
      alertmanagerConfigRepo.getConfig
        .map(_.asJson.asYaml)
        .flatMap(Ok(_))

    case request@POST -> Root / "api" / "v1" / "alerts" =>
      request.as[YamlSyntax].flatMap { yaml =>
        val config = yaml.tree.as[AlertmanagerConfig].toTry.get
        alertmanagerConfigRepo.setConfig(config) >>
          reloadRules >>
          Created()
      }

    case DELETE -> Root / "api" / "v1" / "alerts" =>
      alertmanagerConfigRepo.deleteConfig >>
        reloadRules >>
        Ok()

    case request@_ -> "alertmanager" /: pathInfo =>
      httpApp(request.withPathInfo(pathInfo)).map { response =>
        logger.debug(s"${request.method} ${request.uri.path} -> ${response.status.code}")
        response
      }
  }
}

object AlertmanagerRoutes {
  def apply(
             client: Client[IO],
             alertmanagerUrl: Uri,
             alertmanagerConfigRepo: AlertmanagerConfigRepo[IO],
             warnDelay: FiniteDuration
           ): IO[AlertmanagerRoutes] = {
    val routes = new AlertmanagerRoutes(
      client = client,
      alertmanagerUrl = alertmanagerUrl,
      alertmanagerConfigRepo = alertmanagerConfigRepo,
      warnDelay = warnDelay
    )

    def reloadSchedule: IO[Unit] =
      (routes.reloadRules >> reloadSchedule).delayBy(5.minutes)

    reloadSchedule.start.as(routes)
  }
}
