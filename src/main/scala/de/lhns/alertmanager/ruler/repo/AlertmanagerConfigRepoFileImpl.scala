package de.lhns.alertmanager.ruler.repo

import cats.effect.std.Semaphore
import cats.effect.{Async, Sync}
import cats.syntax.functor._
import de.lhns.alertmanager.ruler.model.AlertmanagerConfig
import io.circe.yaml.syntax._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

// TODO: implement template_files
class AlertmanagerConfigRepoFileImpl[F[_] : Sync] private(
                                                           semaphore: Semaphore[F],
                                                           filePath: Path
                                                         ) extends AlertmanagerConfigRepo[F] {
  private val readConfig: F[AlertmanagerConfig] = Sync[F].blocking {
    if (Files.exists(filePath)) {
      val configString = Files.readString(filePath)
      AlertmanagerConfig(
        templateFiles = Map.empty,
        alertmanagerConfig = {
          io.circe.yaml.parser.parse(configString)
            .toTry.get
        }
      )
    } else
      AlertmanagerConfig.empty
  }

  private def writeConfig(config: AlertmanagerConfig): F[Unit] = Sync[F].blocking {
    val configString = config.alertmanagerConfig.asYaml.spaces2
    Files.writeString(filePath, configString, StandardCharsets.UTF_8)
  }

  override def getConfig: F[AlertmanagerConfig] = semaphore.permit.use { _ =>
    readConfig
  }

  override def setConfig(config: AlertmanagerConfig): F[Unit] = semaphore.permit.use { _ =>
    writeConfig(config)
  }

  override def deleteConfig: F[Unit] = semaphore.permit.use { _ =>
    writeConfig(AlertmanagerConfig.empty)
  }
}

object AlertmanagerConfigRepoFileImpl {
  def apply[F[_] : Async](
                           filePath: Path
                         ): F[AlertmanagerConfigRepoFileImpl[F]] =
    Semaphore[F](1).map(new AlertmanagerConfigRepoFileImpl[F](_, filePath))
}
