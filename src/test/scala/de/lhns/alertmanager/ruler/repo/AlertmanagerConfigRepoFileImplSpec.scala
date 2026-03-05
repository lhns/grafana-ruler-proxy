package de.lhns.alertmanager.ruler.repo

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import io.circe.Json
import munit.FunSuite

import java.nio.file.Files

class AlertmanagerConfigRepoFileImplSpec extends FunSuite {
  test("set/get/delete config roundtrip") {
    val file = Files.createTempFile("alertmanager-config", ".yml")
    try {
      val config = AlertmanagerConfig(
        alertmanagerConfig = Json.obj(
          "route" -> Json.obj("receiver" -> Json.fromString("default")),
          "receivers" -> Json.arr(Json.obj("name" -> Json.fromString("default")))
        ),
        templateFiles = Map("template.tmpl" -> Json.fromString("{{ define \"x\" }}x{{ end }}"))
      )

      val program = for {
        repo <- AlertmanagerConfigRepoFileImpl[IO](filePath = file)
        _ <- repo.setConfig(config)
        stored <- repo.getConfig
        _ <- repo.deleteConfig
        afterDelete <- repo.getConfig
      } yield (stored, afterDelete)

      val (stored, afterDelete) = program.unsafeRunSync()

      assertEquals(stored.alertmanagerConfig, config.alertmanagerConfig)
      assertEquals(stored.templateFiles, Map.empty)
      assertEquals(afterDelete, AlertmanagerConfig.empty)
    } finally {
      Files.deleteIfExists(file)
    }
  }
}
