addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.12.0")

lazy val root = (project in file(".")).
  settings(
    sbtPlugin := true,
    name := "sbt-spark-package",
    organization := "org.spark-packages",
    version := "0.1",
    description := "sbt plugin to develop, use, and publish Spark Packages",
    licenses := Seq("Apache-2.0 License" -> url("https://github.com/brkyvz/sbt-spark-packages/blob/master/LICENSE")),
    publishTo := Some("Spark Package Test Repo" at
      s"https://api.bintray.com/content/brkyvz/maven/spark-packages_sbt-spark-package/${version.value}"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials")
  )


