lazy val root = (project in file(".")).
  settings(
    sbtPlugin := true,
    name := "sbt-spark-packages",
    organization := "databricks",
    version := "0.1-SNAPSHOT",
    description := "sbt plugin to develop, use, and publish Spark Packages",
    licenses := Seq("Apache-2.10 License" -> url("https://github.com/brkyvz/sbt-spark-packages/blob/master/LICENSE")),
    scalacOptions := Seq("-deprecation", "-unchecked"),
    publishMavenStyle := false,
    credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials")
  )
