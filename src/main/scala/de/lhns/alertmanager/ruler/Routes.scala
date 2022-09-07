package de.lhns.alertmanager.ruler

import cats.data.EitherT
import cats.effect.{Concurrent, IO}
import cats.syntax.functor._
import de.lhns.alertmanager.ruler.RulesConfig.RuleGroup
import de.lolhens.http4s.proxy.Http4sProxy._
import io.circe.Json
import io.circe.yaml.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, HttpRoutes, HttpVersion, MalformedMessageBodyFailure, MediaType, Uri}
import org.log4s.getLogger

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

    HttpRoutes.of {
      case GET -> Root / "config" / "v1" / "rules" =>
        rulesConfig.listRuleGroups
          .map(namespaces => Json.fromFields(namespaces.map {
            case (namespace, groups) => namespace -> Json.fromValues(groups.map(_.json))
          }))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case GET -> Root / "config" / "v1" / "rules" / namespace =>
        rulesConfig.getRuleGroupsByNamespace(namespace)
          .map(groups => Json.fromValues(groups.map(_.json)))
          .map(_.asYaml)
          .flatMap(Ok(_))

      case GET -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        rulesConfig.getRuleGroup(namespace, groupName)
          .map(_.map(_.json.asYaml))
          .flatMap(_.fold(NotFound())(Ok(_)))

      case request@POST -> Root / "config" / "v1" / "rules" / namespace =>
        request.as[YamlSyntax].flatMap { rule =>
          rulesConfig.setRuleGroup(namespace, RuleGroup(rule.tree)).flatMap(_ => Accepted())
        }

      case DELETE -> Root / "config" / "v1" / "rules" / namespace / groupName =>
        rulesConfig.deleteRuleGroup(namespace, groupName).flatMap(_ => Accepted())

      case DELETE -> Root / "config" / "v1" / "rules" / namespace =>
        rulesConfig.deleteNamespace(namespace).flatMap(_ => Accepted())

      case request =>
        logger.debug(request.pathInfo.renderString)

        httpApp(
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
    }
  }
}
