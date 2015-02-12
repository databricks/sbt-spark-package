addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.12.0")

lazy val root = (project in file(".")).
  settings(
    sbtPlugin := true,
    name := "sbt-spark-packages",
    organization := "databricks",
    version := "0.1",
    description := "sbt plugin to develop, use, and publish Spark Packages",
    // licenses := Seq("Apache-2.0 License" -> url("https://github.com/brkyvz/sbt-spark-packages/blob/master/LICENSE")),
    publishMavenStyle := false
  )

