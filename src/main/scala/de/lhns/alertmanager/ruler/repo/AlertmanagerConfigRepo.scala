package de.lhns.alertmanager.ruler.repo

import de.lhns.alertmanager.ruler.model.AlertmanagerConfig

trait AlertmanagerConfigRepo[F[_]] {
  def getConfig: F[AlertmanagerConfig]

  def setConfig(config: AlertmanagerConfig): F[Unit]

  def deleteConfig: F[Unit]
}
