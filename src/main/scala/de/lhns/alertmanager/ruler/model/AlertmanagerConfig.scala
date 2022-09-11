package de.lhns.alertmanager.ruler.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class AlertmanagerConfig(
                               template_files: Map[String, String],
                               alertmanager_config: String
                             )

object AlertmanagerConfig {
  implicit val codec: Codec[AlertmanagerConfig] = deriveCodec

  val empty: AlertmanagerConfig = AlertmanagerConfig(
    template_files = Map.empty,
    alertmanager_config = ""
  )
}
