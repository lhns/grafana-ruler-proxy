package de.lhns.alertmanager.ruler

import cats.Functor
import de.lhns.alertmanager.ruler.RulesConfig.RuleGroup
import io.circe.Json

trait RulesConfig[F[_]] {
  def listRuleGroups: F[Map[String, Seq[RuleGroup]]]

  def getRuleGroupsByNamespace(namespace: String): F[Seq[RuleGroup]]

  def getRuleGroup(namespace: String, groupName: String): F[Option[RuleGroup]]

  def setRuleGroup(namespace: String, ruleGroup: RuleGroup): F[Unit]

  def deleteRuleGroup(namespace: String, groupName: String): F[Unit]

  def deleteNamespace(namespace: String): F[Unit]
}

object RulesConfig {
  case class RuleGroup(json: Json) {
    lazy val name: Option[String] = json.hcursor.downField("name").as[String].toOption
  }

  abstract class AbstractRulesConfig[F[_] : Functor] extends RulesConfig[F] {
    def listRuleGroups: F[Map[String, Seq[RuleGroup]]]

  }
}
