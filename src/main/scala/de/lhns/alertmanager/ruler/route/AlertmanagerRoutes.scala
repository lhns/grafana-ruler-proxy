package de.lhns.alertmanager.ruler.route

import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import de.lhns.alertmanager.ruler.repo.AlertmanagerConfigRepo
import io.circe.syntax._
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Uri}
import org.typelevel.log4cats._

import scala.concurrent.duration._

class AlertmanagerRoutes[
  F[_] : Async : LoggerFactory
] private(
           client: Client[F],
           alertmanagerUrl: Uri,
           alertmanagerConfigRepo: AlertmanagerConfigRepo[F],
           warnDelay: FiniteDuration
         ) {
  private val httpApp = client.toHttpApp.warnSlowResponse(warnDelay).proxyTo(alertmanagerUrl)

  val reloadRules: F[Unit] =
    httpApp(reloadRequest).start.void

  def toRoutes: HttpRoutes[F] = {
    object Dsl extends Http4sDsl[F]
    import Dsl._

    HttpRoutes.of[F] {
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
        httpApp(request.withPathInfo(pathInfo))
    }
  }
}

object AlertmanagerRoutes {
  def apply[F[_] : Async : LoggerFactory](
                                           client: Client[F],
                                           alertmanagerUrl: Uri,
                                           alertmanagerConfigRepo: AlertmanagerConfigRepo[F],
                                           warnDelay: FiniteDuration
                                         ): F[AlertmanagerRoutes[F]] = {
    val routes = new AlertmanagerRoutes[F](
      client = client,
      alertmanagerUrl = alertmanagerUrl,
      alertmanagerConfigRepo = alertmanagerConfigRepo,
      warnDelay = warnDelay
    )

    def reloadSchedule: F[Unit] =
      (routes.reloadRules >> reloadSchedule).delayBy(5.minutes)

    reloadSchedule.start.as(routes)
  }
}
