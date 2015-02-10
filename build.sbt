name := "sbt-spark-packages-dev"

version := "0.1"

scalaVersion := "2.10.4"

lazy val root = (project in file(".")).
  settings(
    sbtPlugin := true,
    name := "sbt-spark-packages",
    organization := "databricks",
    version := "0.1-SNAPSHOT",
    description := "sbt plugin to develop, use, and publish Spark Packages",
    licenses := Seq("Apache-2.10 License" -> url("https://github.com/brkyvz/sbt-spark-packages/blob/master/LICENSE")),
    publishMavenStyle := false,
    credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials")
  )
    