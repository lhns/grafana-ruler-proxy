package de.lhns.alertmanager.ruler.route

import cats.effect.IO
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import de.lhns.alertmanager.ruler.repo.AlertmanagerConfigRepo
import io.circe.syntax._
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Uri}
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

import scala.concurrent.duration._

class AlertmanagerRoutes private(
                                  client: Client[IO],
                                  alertmanagerUrl: Uri,
                                  alertmanagerConfigRepo: AlertmanagerConfigRepo[IO],
                                  warnDelay: FiniteDuration
                                ) {
  private implicit val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

  private val httpApp = client.toHttpApp.warnSlowResponse(warnDelay).proxyTo(alertmanagerUrl)

  def reloadRules: IO[Unit] =
    httpApp(reloadRequest).start.void

  def toRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "v1" / "alerts" =>
      // https://prometheus.io/docs/prometheus/latest/querying/api/#alerts
      // https://grafana.com/docs/mimir/latest/references/http-api/#get-alertmanager-configuration
      alertmanagerConfigRepo.getConfig
        .map(_.asJson.asYaml)
        .flatMap(Ok(_))

    case request@POST -> Root / "api" / "v1" / "alerts" =>
      // https://grafana.com/docs/mimir/latest/references/http-api/#set-alertmanager-configuration
      request.as[YamlSyntax].flatMap { yaml =>
        val config = yaml.tree.as[AlertmanagerConfig].toTry.get
        alertmanagerConfigRepo.setConfig(config) >>
          reloadRules >>
          Created()
      }

    case DELETE -> Root / "api" / "v1" / "alerts" =>
      // https://grafana.com/docs/mimir/latest/references/http-api/#delete-alertmanager-configuration
      alertmanagerConfigRepo.deleteConfig >>
        reloadRules >>
        Ok()

    case request@_ -> "alertmanager" /: pathInfo =>
      httpApp(request.withPathInfo(pathInfo)).flatMap { response =>
        Logger[IO].debug(s"${request.method} ${request.uri.path} -> ${response.status.code}")
          .as(response)
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
