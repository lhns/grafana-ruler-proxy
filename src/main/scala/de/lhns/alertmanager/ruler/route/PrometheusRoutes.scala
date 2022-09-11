package de.lhns.alertmanager.ruler.route

import cats.data.OptionT
import cats.effect.IO
import de.lhns.alertmanager.ruler.model.RuleGroup
import de.lhns.alertmanager.ruler.repo.RulesConfigRepo
import de.lolhens.http4s.proxy.Http4sProxy._
import fs2.Chunk
import io.circe.Json
import io.circe.yaml.syntax._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, HttpVersion, Method, Request, Response, Status, Uri}
import org.log4s.getLogger

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class PrometheusRoutes private(
                                client: Client[IO],
                                prometheusUrl: Uri,
                                alertmanagerConfigApiEnabled: Boolean,
                                rulesConfigRepo: RulesConfigRepo[IO]
                              ) {
  private val logger = getLogger

  private val httpApp = client.toHttpApp

  private def warnSlowResponse[A](io: IO[A]): IO[A] =
    for {
      fiber <- IO {
        logger.warn(s"request to $prometheusUrl is taking longer than expected")
      }.delayBy(10.seconds).start
      result <- io
      _ <- fiber.cancel
    } yield result

  private def proxyRequest(request: Request[IO]): IO[Response[IO]] =
    warnSlowResponse(httpApp(
      request
        .withHttpVersion(HttpVersion.`HTTP/1.1`)
        .withDestination(
          request.uri
            .withSchemeAndAuthority(prometheusUrl)
            .withPath((
              if (request.pathInfo.isEmpty) prometheusUrl.path
              else prometheusUrl.path.concat(request.pathInfo)
              ).toAbsolute)
        )
    ))

  def reloadRules: IO[Unit] =
    proxyRequest(Request[IO](
      method = Method.POST,
      uri = prometheusUrl / "-" / "reload"
    )).start.void

  def toRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request@GET -> Root / "api" / "v1" / "status" / "buildinfo" =>
      proxyRequest(request).flatMap { response =>
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

    case GET -> Root / "config" / "v1" / "rules" =>
      rulesConfigRepo.listRuleGroups
        .map(namespaces => Json.fromFields(namespaces.map {
          case (namespace, groups) => namespace -> Json.fromValues(groups.map(_.json))
        }))
        .map(_.asYaml)
        .flatMap(Ok(_))

    case GET -> Root / "config" / "v1" / "rules" / namespace =>
      rulesConfigRepo.getRuleGroupsByNamespace(namespace)
        .map(groups => Json.fromValues(groups.map(_.json)))
        .map(_.asYaml)
        .flatMap(Ok(_))

    case GET -> Root / "config" / "v1" / "rules" / namespace / groupName =>
      OptionT(rulesConfigRepo.getRuleGroup(namespace, groupName))
        .map(_.json)
        .getOrElse(Json.obj(
          "name" -> Json.fromString(groupName),
          "rules" -> Json.arr()
        ))
        .map(_.asYaml)
        .flatMap(Ok(_))

    case request@POST -> Root / "config" / "v1" / "rules" / namespace =>
      request.as[YamlSyntax].flatMap { rule =>
        rulesConfigRepo.setRuleGroup(namespace, RuleGroup(rule.tree)) >>
          reloadRules >>
          Accepted(Json.obj())
      }

    case DELETE -> Root / "config" / "v1" / "rules" / namespace / groupName =>
      rulesConfigRepo.deleteRuleGroup(namespace, groupName) >>
        reloadRules >>
        Accepted(Json.obj())

    case DELETE -> Root / "config" / "v1" / "rules" / namespace =>
      rulesConfigRepo.deleteNamespace(namespace) >>
        reloadRules >>
        Accepted(Json.obj())

    case request =>
      proxyRequest(request).map { response =>
        logger.info(s"${request.method} ${request.uri.path} -> ${response.status.code}")
        response
      }
  }
}

object PrometheusRoutes {
  def apply(
             client: Client[IO],
             prometheusUrl: Uri,
             alertmanagerConfigApiEnabled: Boolean,
             rulesConfigRepo: RulesConfigRepo[IO]
           ): IO[PrometheusRoutes] = {
    val routes = new PrometheusRoutes(
      client = client,
      prometheusUrl = prometheusUrl,
      alertmanagerConfigApiEnabled = alertmanagerConfigApiEnabled,
      rulesConfigRepo = rulesConfigRepo
    )

    def reloadSchedule: IO[Unit] =
      (routes.reloadRules >> reloadSchedule).delayBy(5.minutes)

    reloadSchedule.start.as(routes)
  }
}
