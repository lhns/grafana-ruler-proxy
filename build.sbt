ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val V = new {
  val catsEffect = "3.3.14"
  val circe = "0.14.2"
  val circeConfig = "0.8.0"
  val circeOptics = "0.14.1"
  val circeYaml = "0.14.1"
  val http4s = "0.23.15"
  val http4sJdkHttpClient = "0.7.0"
  val http4sProxy = "0.4.0"
  val logbackClassic = "1.4.3"
  val munit = "0.7.29"
  val munitTaglessFinal = "0.2.0"
  val proxyVole = "1.0.17"
  val scalaTrustmanagerUtils = "0.3.4"
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "de.lolhens" %% "munit-tagless-final" % V.munitTaglessFinal % Test,
    "org.scalameta" %% "munit" % V.munit % Test
  ),
  testFrameworks += new TestFramework("munit.Framework"),
  assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",
  assembly / assemblyOption := (assembly / assemblyOption).value
    .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),
  assembly / assemblyMergeStrategy := {
    case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "grafana-ruler-proxy",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
      "de.lolhens" %% "http4s-proxy" % V.http4sProxy,
      "de.lolhens" %% "scala-trustmanager-utils" % V.scalaTrustmanagerUtils,
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % V.proxyVole,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-ember-server" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
      "org.typelevel" %% "cats-effect" % V.catsEffect,
      "io.circe" %% "circe-config" % V.circeConfig,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-optics" % V.circeOptics,
      "io.circe" %% "circe-parser" % V.circe,
      "io.circe" %% "circe-yaml" % V.circeYaml,
    )
  )
