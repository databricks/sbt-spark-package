package sbtsparkpackages

import sbt._
import Keys._
import sbtassembly._
import sbtassembly.AssemblyKeys._
import Path.relativeTo

object SparkPackagesPlugin extends AutoPlugin {

  object autoImport {
    val sparkPackageName = settingKey[String]("The name of the Spark Package")
    val sparkComponents = settingKey[Seq[String]](
      "The components of Spark this package depends on. e.g. mllib, sql, graphx, streaming. Spark " +
        "Core is included by default if this key is not set.")
    val sparkVersion = settingKey[String]("The version of Spark to build against.")
    val sparkPackageDependencies = settingKey[Seq[String]]("The Spark Package dependencies")
    val sparkPackageNamespace = settingKey[String]("The namespace to use for shading while building " +
      "the assembly jar.")
    val spGeneratePom = settingKey[Boolean]("Whether to generate a pom file while packaging.")
    val spMakeDistribution = taskKey[Unit]("Generate a zip archive for distribution on the Spark Packages website.")
    val spZipDirectory = settingKey[File]("Directory to output the zip archive.")
  }

  import autoImport._

  override def requires = plugins.JvmPlugin && AssemblyPlugin
  override def trigger = allRequirements

  override lazy val buildSettings: Seq[Setting[_]] = {
    super.buildSettings ++ Seq(
      sparkPackageDependencies := Seq(),
      sparkComponents := Seq(),
      spGeneratePom := true,
      sparkVersion := "1.2.0",
      sparkPackageName := "dummy"
    )
  }

  def listFilesRecursively(dir: File): Seq[File] = {
    val list = IO.listFiles(dir)
    list.filter(_.isFile) ++ list.filter(_.isDirectory).flatMap(listFilesRecursively)
  }

  override lazy val projectSettings: Seq[Setting[_]] = {
    super.projectSettings ++ Seq(
      resolvers += "Spark Packages repo" at "https://dl.bintray.com/spark-packages/maven/",
      // add any Spark dependencies
      libraryDependencies ++= {
        val sparkComponentSet = sparkComponents.value.toSet
        if (sparkComponentSet.size == 0) {
          Seq("org.apache.spark" %% s"spark-core" % sparkVersion.value % "provided")
        } else {
          sparkComponentSet.map { component =>
            "org.apache.spark" %% s"spark-$component" % sparkVersion.value % "provided"
          }.toSeq
        }
      },
      // add any Spark Package dependencies
      libraryDependencies ++= sparkPackageDependencies.value.map { sparkPackage =>
        val splits = sparkPackage.split(":")
        require(splits.length == 2,
          "Spark Packages must be provided in the format: package_name:version.")
        val spVersion = splits(1)
        val names = splits(0).split("/")
        require(names.length == 2,
          "The package_name is provided in the format: org_name/repo_name.")
        names(0) % names(1) % spVersion
      },
      sparkPackageNamespace := sparkPackageName.value.replace("/", "_"),
      // When making a distribution, make sure that the pom writes "provided" for Spark
      libraryDependencies in spMakeDistribution := libraryDependencies.value.map { dep =>
        if (dep.organization == "org.apache.spark") {
          dep.configurations match {
            case Some("provided") => dep
            case _ => new ModuleID(dep.organization, dep.name, dep.revision, Some("provided"))
          }
        } else {
          dep
        }
      },
      libraryDependencies in assembly := libraryDependencies.value.map { dep =>
        if (dep.organization == "org.apache.spark") {
          dep.configurations match {
            case Some("provided") => dep
            case _ => new ModuleID(dep.organization, dep.name, dep.revision, Some("provided"))
          }
        } else {
          dep
        }
      },
      // add any Python binaries when making a distribution
      mappings in (Compile, packageBin in spMakeDistribution) := (mappings in (Compile, packageBin)).value ++ {
        val pythonDirectory: Seq[File] = listFilesRecursively(baseDirectory.value / "python")
        val pythonBase = baseDirectory.value / "python"
        pythonDirectory.filter(f => f.getPath().indexOf("lib") == -1 && f.getPath().indexOf("bin") == -1 &&
          f.getPath().indexOf("doc") == -1)
          .filter(f => f.getPath().indexOf(".pyc") > -1) ++
          Seq(baseDirectory.value / "python" / "requirements.txt") pair relativeTo(pythonBase)
      },
      spMakeDistribution := {
        val jar = (packageBin in Compile).value
        val pom = (makePom in Compile).value
        val names = sparkPackageName.value.split("/")
        require(names.length == 2,
          s"The package_name must be provided in the format: org_name/repo_name. Currently: ${sparkPackageName.value}")
        val spArtifactName = names(1) + "-" + version.value

        val zipFile = (target.value / (spArtifactName + ".zip"))

        IO.delete(zipFile)
        IO.zip(Seq(jar -> (spArtifactName + ".jar"), pom -> (spArtifactName + ".pom")), zipFile)
        zipFile
      }
    )
  }
}


