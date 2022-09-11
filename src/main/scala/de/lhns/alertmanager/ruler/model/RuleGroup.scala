package de.lhns.alertmanager.ruler.model

import io.circe.Json

case class RuleGroup(json: Json) {
  lazy val name: Option[String] = json.hcursor.downField("name").as[String].toOption
}
