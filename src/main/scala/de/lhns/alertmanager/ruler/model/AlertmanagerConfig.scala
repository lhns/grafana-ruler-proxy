package de.lhns.alertmanager.ruler.model

import cats.syntax.traverse._
import io.circe.generic.semiauto.deriveCodec
import io.circe.yaml.parser.{parse => parseYaml}
import io.circe.yaml.syntax._
import io.circe.{Codec, Json}

case class AlertmanagerConfig(
                               alertmanagerConfig: Json,
                               templateFiles: Map[String, Json]
                             )

object AlertmanagerConfig {
  case class AlertmanagerConfigJson(
                                     alertmanager_config: String,
                                     template_files: Map[String, String]
                                   )

  object AlertmanagerConfigJson {
    implicit val codec: Codec[AlertmanagerConfigJson] = deriveCodec
  }

  implicit val codec: Codec[AlertmanagerConfig] = Codec.from(
    AlertmanagerConfigJson.codec.emapTry { alertmanagerConfigJson =>
      (for {
        alertmanagerConfig <- parseYaml(alertmanagerConfigJson.alertmanager_config)
        templateFiles <- alertmanagerConfigJson.template_files.toSeq.map {
          case (key, value) => parseYaml(value).map((key, _))
        }.sequence
      } yield AlertmanagerConfig(
        alertmanagerConfig = alertmanagerConfig,
        templateFiles = templateFiles.toMap
      )).toTry
    },
    AlertmanagerConfigJson.codec.contramap { alertmanagerConfig =>
      AlertmanagerConfigJson(
        alertmanager_config = alertmanagerConfig.alertmanagerConfig.asYaml.spaces2,
        template_files = alertmanagerConfig.templateFiles.map {
          case (key, value) => (key, value.asYaml.spaces2)
        }
      )
    }
  )

  val empty: AlertmanagerConfig = AlertmanagerConfig(
    alertmanagerConfig = Json.obj(),
    templateFiles = Map.empty
  )
}
