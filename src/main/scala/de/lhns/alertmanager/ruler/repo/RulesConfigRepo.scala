package de.lhns.alertmanager.ruler.repo

import de.lhns.alertmanager.ruler.model.RuleGroup

trait RulesConfigRepo[F[_]] {
  def listRuleGroups: F[Map[String, Seq[RuleGroup]]]

  def getRuleGroupsByNamespace(namespace: String): F[Seq[RuleGroup]]

  def getRuleGroup(namespace: String, groupName: String): F[Option[RuleGroup]]

  def setRuleGroup(namespace: String, ruleGroup: RuleGroup): F[Unit]

  def deleteRuleGroup(namespace: String, groupName: String): F[Unit]

  def deleteNamespace(namespace: String): F[Unit]
}
