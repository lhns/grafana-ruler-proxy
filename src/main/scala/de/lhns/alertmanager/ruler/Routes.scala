package de.lhns.alertmanager.ruler

import cats.data.{EitherT, OptionT}
import cats.effect.{Concurrent, IO}
import cats.syntax.functor._
import de.lhns.alertmanager.ruler.RulesConfig.RuleGroup
import de.lolhens.http4s.proxy.Http4sProxy._
import fs2.Chunk
import io.circe.Json
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, HttpRoutes, HttpVersion, MalformedMessageBodyFailure, MediaType, Request, Response, Status, Uri}
import org.log4s.getLogger
import org.http4s.circe._

import java.nio.charset.StandardCharsets

object Routes {
  private val logger = getLogger

  private implicit def yamlDecoder[F[_] : Concurrent]: EntityDecoder[F, YamlSyntax] =
    EntityDecoder.decodeBy[F, YamlSyntax](MediaType.text.yaml) { media =>
      EitherT(media.as[String].map(io.circe.yaml.parser.parse))
        .leftMap[DecodeFailure](failure => MalformedMessageBodyFailure("Invalid YAML", Some(failure)))
        .map(_.asYaml)
    }

  private implicit def yamlEncoder[F[_] : Concurrent]: EntityEncoder[F, YamlSyntax] =
    EntityEncoder.stringEncoder[F]
      .withContentType(`Content-Type`(MediaType.text.yaml))
      .contramap(_.spaces2)

  def apply(
             client: Client[IO],
             alertmanagerUrl: Uri,
             rulesConfig: RulesConfig[IO]
           ): HttpRoutes[IO] = {
    val httpApp = client.toHttpApp

    def alertmanager(request: Request[IO]): IO[Response[IO]] = httpApp(
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

    HttpRoutes.of {
      case request@GET -> Root / "api" / "v1" / "status" / "buildinfo" =>
        alertmanager(request).flatMap { response =>
          OptionT.whenF(response.status.isSuccess) {
            response.as[Json]
          }.getOrElse(
            Json.obj(
              "status" -> Json.fromString("success")
            )
          ).map { buildinfo =>
            val newBuildinfo = buildinfo.deepMerge(Json.obj(
              "data" -> Json.obj(
                "features" -> Json.obj(
                  "ruler_config_api" -> Json.fromString("true"),
                  "alertmanager_config_api" -> Json.fromString("true")
                )
              )
            ))

            response
              .withStatus(Status.Ok)
              .withEntity(newBuildinfo)
          }
        }

      case request@GET -> Root / "config" / "v1" / "rules" =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        rulesConfig.listRuleGroups
          .map(namespaces => Json.fromFields(namespaces.map {
            case (namespace, groups) => namespace -> Json.fromValues(groups.map(_.json))
          }))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case request@GET -> Root / "config" / "v1" / "rules" / namespace =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        rulesConfig.getRuleGroupsByNamespace(namespace)
          .map(groups => Json.fromValues(groups.map(_.json)))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case request@GET -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        OptionT(rulesConfig.getRuleGroup(namespace, groupName))
          .map(_.json)
          .getOrElse(Json.obj(
            "name" -> Json.fromString(groupName),
            "rules" -> Json.arr()
          ))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case request@POST -> Root / "config" / "v1" / "rules" / namespace =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        request.as[YamlSyntax].flatMap { rule =>
          rulesConfig.setRuleGroup(namespace, RuleGroup(rule.tree)).flatMap(_ => Accepted(Json.obj()))
        }

      case request@DELETE -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        rulesConfig.deleteRuleGroup(namespace, groupName).flatMap(_ => Accepted(Json.obj()))

      case request@DELETE -> Root / "config" / "v1" / "rules" / namespace =>
        logger.debug(s"${request.method} ${request.pathInfo}")
        rulesConfig.deleteNamespace(namespace).flatMap(_ => Accepted(Json.obj()))

      /*case GET -> Root / "api" / "v2" / "status" =>
        NotFound()

      case request@_ -> "alertmanager" /: path =>
        alertmanager(request.withPathInfo(path)).map { response =>
          logger.debug(s"${request.method} ${request.pathInfo} -> ${response.status.code}")
          response
        }*/

      case request =>
        alertmanager(request).flatMap { response =>
          logger.debug(s"${request.method} ${request.pathInfo} -> ${response.status.code}")
          response.as[Chunk[Byte]].map { chunk =>
            println(new String(chunk.toArray, StandardCharsets.UTF_8))
            response.withBodyStream(fs2.Stream.chunk(chunk))
          }
        }
    }
  }
}
