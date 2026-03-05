package de.lhns.alertmanager.ruler.repo

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import de.lhns.alertmanager.ruler.model.RuleGroup
import io.circe.Json
import munit.FunSuite

import java.nio.file.Files

class RulesConfigRepoFileImplSpec extends FunSuite {
  private def group(name: String): RuleGroup = RuleGroup(
    Json.obj(
      "name" -> Json.fromString(name),
      "rules" -> Json.arr()
    )
  )

  test("set/list/get/delete rule groups in configured namespace") {
    val file = Files.createTempFile("rules-config", ".yml")
    try {
      val program = for {
        repo <- RulesConfigRepoFileImpl[IO](filePath = file, namespace = "ns")
        _ <- repo.setRuleGroup("ns", group("g1"))
        _ <- repo.setRuleGroup("ns", group("g2"))
        list <- repo.listRuleGroups
        g1 <- repo.getRuleGroup("ns", "g1")
        _ <- repo.deleteRuleGroup("ns", "g1")
        afterDelete <- repo.getRuleGroupsByNamespace("ns")
        _ <- repo.deleteNamespace("ns")
        afterNamespaceDelete <- repo.getRuleGroupsByNamespace("ns")
      } yield (list, g1, afterDelete, afterNamespaceDelete)

      val (list, g1, afterDelete, afterNamespaceDelete) = program.unsafeRunSync()

      assertEquals(list.keySet, Set("ns"))
      assertEquals(list("ns").flatMap(_.name), Seq("g1", "g2"))
      assertEquals(g1.flatMap(_.name), Some("g1"))
      assertEquals(afterDelete.flatMap(_.name), Seq("g2"))
      assertEquals(afterNamespaceDelete, Seq.empty)
    } finally {
      Files.deleteIfExists(file)
    }
  }

  test("operations on other namespaces are no-ops") {
    val file = Files.createTempFile("rules-config", ".yml")
    try {
      val program = for {
        repo <- RulesConfigRepoFileImpl[IO](filePath = file, namespace = "ns")
        _ <- repo.setRuleGroup("other", group("ignored"))
        groups <- repo.getRuleGroupsByNamespace("ns")
      } yield groups

      assertEquals(program.unsafeRunSync(), Seq.empty)
    } finally {
      Files.deleteIfExists(file)
    }
  }
}
