package de.lhns.alertmanager.ruler.route

import cats.data.OptionT
import cats.effect.IO
import de.lhns.alertmanager.ruler.model.RuleGroup
import de.lhns.alertmanager.ruler.repo.RulesConfigRepo
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.yaml.syntax._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.middleware.GZip
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Status, Uri}
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

import scala.concurrent.duration._

class PrometheusRoutes private(
                                client: Client[IO],
                                prometheusUrl: Uri,
                                alertmanagerConfigApiEnabled: Boolean,
                                rulesConfigRepo: RulesConfigRepo[IO],
                                namespaceMappings: Map[String, String],
                                warnDelay: FiniteDuration
                              ) {
  private implicit val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

  private val httpApp = client.toHttpApp.warnSlowResponse(warnDelay).proxyTo(prometheusUrl)
  private val gzipHttpApp = GZip()(client).toHttpApp.warnSlowResponse(warnDelay).proxyTo(prometheusUrl)

  def reloadRules: IO[Unit] =
    httpApp(reloadRequest).start.void

  def toRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request@GET -> Root / "api" / "v1" / "status" / "buildinfo" =>
      // https://prometheus.io/docs/prometheus/latest/querying/api/#build-information
      httpApp(request).flatMap { response =>
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
      gzipHttpApp(request).flatMap { response =>
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
      httpApp(request).flatMap { response =>
        Logger[IO].debug(s"${request.method} ${request.uri.path} -> ${response.status.code}")
          .as(response)
      }
  }
}

object PrometheusRoutes {
  def apply(
             client: Client[IO],
             prometheusUrl: Uri,
             alertmanagerConfigApiEnabled: Boolean,
             rulesConfigRepo: RulesConfigRepo[IO],
             namespaceMappings: Map[String, String],
             warnDelay: FiniteDuration
           ): IO[PrometheusRoutes] = {
    val routes = new PrometheusRoutes(
      client = client,
      prometheusUrl = prometheusUrl,
      alertmanagerConfigApiEnabled = alertmanagerConfigApiEnabled,
      rulesConfigRepo = rulesConfigRepo,
      namespaceMappings = namespaceMappings,
      warnDelay = warnDelay
    )

    def reloadSchedule: IO[Unit] =
      (routes.reloadRules >> reloadSchedule).delayBy(5.minutes)

    reloadSchedule.start.as(routes)
  }
}
