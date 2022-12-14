package de.lhns.alertmanager.ruler.repo

import cats.Monad
import cats.effect.std.Semaphore
import cats.effect.{Async, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import de.lhns.alertmanager.ruler.model.RuleGroup
import io.circe.Json
import io.circe.yaml.syntax._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class RulesConfigRepoFileImpl[F[_] : Sync] private(
                                                    semaphore: Semaphore[F],
                                                    filePath: Path,
                                                    namespace: String
                                                  ) extends RulesConfigRepo[F] {
  private val readRuleGroups: F[Seq[RuleGroup]] = Sync[F].blocking {
    if (Files.exists(filePath)) {
      val ruleGroupsString = Files.readString(filePath)
      io.circe.yaml.parser.parse(ruleGroupsString)
        .toTry.get
        .hcursor.downField("groups").focus.flatMap(_.asArray)
        .getOrElse(Seq.empty)
        .map(RuleGroup)
    } else
      Seq.empty
  }

  private def writeRuleGroups(ruleGroups: Seq[RuleGroup]): F[Unit] = Sync[F].blocking {
    val ruleGroupsString = Json.obj("groups" -> Json.fromValues(ruleGroups.map(_.json))).asYaml.spaces2
    Files.writeString(filePath, ruleGroupsString, StandardCharsets.UTF_8)
  }

  override def listRuleGroups: F[Map[String, Seq[RuleGroup]]] = semaphore.permit.use { _ =>
    for {
      ruleGroups <- readRuleGroups
    } yield
      Map(this.namespace -> ruleGroups)
  }

  def getRuleGroupsByNamespace(namespace: String): F[Seq[RuleGroup]] =
    listRuleGroups.map(_.getOrElse(namespace, Seq.empty))

  def getRuleGroup(namespace: String, groupName: String): F[Option[RuleGroup]] =
    getRuleGroupsByNamespace(namespace).map(_.find(_.name.contains(groupName)))

  override def setRuleGroup(namespace: String, ruleGroup: RuleGroup): F[Unit] =
    if (namespace == this.namespace) {
      semaphore.permit.use { _ =>
        for {
          ruleGroups <- readRuleGroups
          newRuleGroups = ruleGroups.filterNot(_.name.exists(ruleGroup.name.contains)) :+ ruleGroup
          _ <- writeRuleGroups(newRuleGroups)
        } yield ()
      }
    } else
      Monad[F].unit

  override def deleteRuleGroup(namespace: String, groupName: String): F[Unit] =
    if (namespace == this.namespace) {
      semaphore.permit.use { _ =>
        for {
          ruleGroups <- readRuleGroups
          newRuleGroups = ruleGroups.filterNot(_.name.contains(groupName))
          _ <- writeRuleGroups(newRuleGroups)
        } yield ()
      }
    } else
      Monad[F].unit

  override def deleteNamespace(namespace: String): F[Unit] =
    if (namespace == this.namespace) {
      semaphore.permit.use { _ =>
        writeRuleGroups(Seq.empty)
      }
    } else
      Monad[F].unit
}

object RulesConfigRepoFileImpl {
  def apply[F[_] : Async](
                           filePath: Path,
                           namespace: String
                         ): F[RulesConfigRepoFileImpl[F]] =
    Semaphore[F](1).map(new RulesConfigRepoFileImpl[F](_, filePath, namespace))
}
