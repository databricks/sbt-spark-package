import sbt._
import sbt.Keys._
import sbtsparkpackage.SparkPackagePlugin.autoImport._
import sbtassembly._
import sbtassembly.AssemblyKeys._

object Shading extends Build {
  lazy val commonSettings = Seq(
    version := "0.1",
    name := "shading",
    scalaVersion := "2.10.6",
    organization := "great.test"
  )

  lazy val nonShadedDependencies = Seq(
    ModuleID("org.apache.commons", "commons-proxy", "1.0")
  )

  lazy val shaded = Project("shaded", file(".")).settings(
    libraryDependencies ++= (Seq("org.apache.commons" % "commons-weaver-antlib" % "1.2") ++
      nonShadedDependencies.map(_ % "provided")),
    target := target.value / "shaded",
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.apache.commons.**" -> "databricks.commons.@1").inAll
    )
  ).settings(commonSettings: _*)

  lazy val distribute = Project("distribution", file(".")).settings(
    spName := "test/shading",
    target := target.value / "distribution",
    spShade := true,
    assembly in spPackage := (assembly in shaded).value,
    libraryDependencies := nonShadedDependencies
  ).settings(commonSettings: _*)
}
