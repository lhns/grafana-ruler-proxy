package de.lhns.alertmanager.ruler.route

import cats.data.OptionT
import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import de.lhns.alertmanager.ruler.model.RuleGroup
import de.lhns.alertmanager.ruler.repo.RulesConfigRepo
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.yaml.syntax._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.middleware.GZip
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Status, Uri}
import org.typelevel.log4cats._

import scala.concurrent.duration._

class PrometheusRoutes[
  F[_] : Async : LoggerFactory
] private(
           client: Client[F],
           rulesUrl: Uri,
           prometheusUrl: Uri,
           alertmanagerConfigApiEnabled: Boolean,
           rulesConfigRepo: RulesConfigRepo[F],
           namespaceMappings: Map[String, String],
           warnDelay: FiniteDuration
         ) {
  private val prometheusApp = client.toHttpApp.warnSlowResponse(warnDelay).proxyTo(prometheusUrl)
  private val rulesGzipApp = GZip()(client).toHttpApp.warnSlowResponse(warnDelay).proxyTo(rulesUrl)

  val reloadRules: F[Unit] =
    rulesGzipApp(reloadRequest).start.void

  def toRoutes: HttpRoutes[F] = {
    object Dsl extends Http4sDsl[F]
    import Dsl._

    HttpRoutes.of[F] {
      case request@GET -> Root / "api" / "v1" / "status" / "buildinfo" =>
        // https://prometheus.io/docs/prometheus/latest/querying/api/#build-information
        rulesGzipApp(request).flatMap { response =>
          OptionT.whenF(response.status.isSuccess) {
              response.as[Json]
            }
            .getOrElse(Json.obj(
              "status" -> Json.fromString("success")
            ))
            .map { buildinfo =>
              val newBuildinfo = buildinfo.deepMerge(Json.obj(
                "data" -> Json.obj(
                  "features" -> Json.obj(
                    "ruler_config_api" -> Json.fromString("true"),
                    "alertmanager_config_api" -> Json.fromString(alertmanagerConfigApiEnabled.toString)
                  )
                )
              ))

              response
                .withStatus(Status.Ok)
                .withEntity(newBuildinfo)
            }
        }

      case request@GET -> Root / "api" / "v1" / "rules" =>
        // https://prometheus.io/docs/prometheus/latest/querying/api/#rules
        rulesGzipApp(request).flatMap { response =>
          OptionT.whenF(response.status.isSuccess) {
            response.as[Json].map { rules =>
              val newRules = root.data.groups.each.file.string.modify(e => namespaceMappings.getOrElse(e, e)).apply(rules)
              response.withEntity(newRules)
            }
          }.getOrElse(response)
        }

      case GET -> Root / "config" / "v1" / "rules" =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#list-rule-groups
        rulesConfigRepo.listRuleGroups
          .map(namespaces => Json.fromFields(namespaces.map {
            case (namespace, groups) => namespace -> Json.fromValues(groups.map(_.json))
          }))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case GET -> Root / "config" / "v1" / "rules" / namespace =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#get-rule-groups-by-namespace
        rulesConfigRepo.getRuleGroupsByNamespace(namespace)
          .map(groups => Json.fromValues(groups.map(_.json)))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case GET -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#get-rule-group
        OptionT(rulesConfigRepo.getRuleGroup(namespace, groupName))
          .map(_.json)
          .getOrElse(Json.obj(
            "name" -> Json.fromString(groupName),
            "rules" -> Json.arr()
          ))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case request@POST -> Root / "config" / "v1" / "rules" / namespace =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#set-rule-group
        request.as[YamlSyntax].flatMap { rule =>
          rulesConfigRepo.setRuleGroup(namespace, RuleGroup(rule.tree)) >>
            reloadRules >>
            Accepted(Json.obj())
        }

      case DELETE -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#delete-rule-group
        rulesConfigRepo.deleteRuleGroup(namespace, groupName) >>
          reloadRules >>
          Accepted(Json.obj())

      case DELETE -> Root / "config" / "v1" / "rules" / namespace =>
        // https://grafana.com/docs/mimir/latest/references/http-api/#delete-namespace
        rulesConfigRepo.deleteNamespace(namespace) >>
          reloadRules >>
          Accepted(Json.obj())

      case request =>
        prometheusApp(request)
    }
  }
}

object PrometheusRoutes {
  def apply[F[_] : Async : LoggerFactory](
                                           client: Client[F],
                                           rulesUrl: Uri,
                                           prometheusUrl: Uri,
                                           alertmanagerConfigApiEnabled: Boolean,
                                           rulesConfigRepo: RulesConfigRepo[F],
                                           namespaceMappings: Map[String, String],
                                           warnDelay: FiniteDuration
                                         ): F[PrometheusRoutes[F]] = {
    val routes = new PrometheusRoutes[F](
      client = client,
      rulesUrl = rulesUrl,
      prometheusUrl = prometheusUrl,
      alertmanagerConfigApiEnabled = alertmanagerConfigApiEnabled,
      rulesConfigRepo = rulesConfigRepo,
      namespaceMappings = namespaceMappings,
      warnDelay = warnDelay
    )

    def reloadSchedule: F[Unit] =
      (routes.reloadRules >> reloadSchedule).delayBy(5.minutes)

    reloadSchedule.start.as(routes)
  }
}
