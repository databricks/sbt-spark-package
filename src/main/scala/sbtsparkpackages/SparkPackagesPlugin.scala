package sbtsparkpackages

import sbt._
import Keys._
import Path.relativeTo
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyKeys.{assembledMappings, assembly, assemblyPackageScala}
import scala.xml.{Elem, Node, Text}

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
    val spMakeDistribution = taskKey[Unit]("Generate a zip archive for distribution on the Spark Packages website.")
    val spMakeAssembly = taskKey[File]("Generate a fat jar including all dependencies (excluding Spark).")
    val spDistributionDirectory = settingKey[File]("Directory to output the zip archive.")
    val spPackWithPython = taskKey[File]("Packs the Jar including Python files")

    val defaultSPSettings = Seq(
      sparkPackageDependencies := Seq(),
      sparkComponents := Seq(),
      sparkVersion := "1.2.0",
      sparkPackageName := "dummy",
      spDistributionDirectory := baseDirectory.value
    )
  }

  import autoImport._

  override def requires = plugins.JvmPlugin && AssemblyPlugin
  override def trigger = allRequirements

  override lazy val buildSettings: Seq[Setting[_]] = defaultSPSettings

  override lazy val projectSettings: Seq[Setting[_]] =
    Defaults.packageTaskSettings(spPackWithPython, mappings in (Compile, packageBin)) ++
      baseSparkPackageSettings

  def listFilesRecursively(dir: File): Seq[File] = {
    val list = IO.listFiles(dir)
    list.filter(_.isFile) ++ list.filter(_.isDirectory).flatMap(listFilesRecursively)
  }

  val validatePackaging =
    Def.task {
      // Make sure Spark configuration is "provided"
      libraryDependencies.value.map { dep =>
        if (dep.organization == "org.apache.spark" && dep.configurations != Some("provided")) {
          sys.error("Please add any Spark dependencies by supplying the sparkVersion " +
            s"and sparkComponents. Please remove: $dep")
          false
        } else if (dep.organization == "org.apache.spark" && dep.revision != sparkVersion.value) {
          sys.error("Please add any Spark dependencies by supplying the sparkVersion " +
            s"and sparkComponents. Please remove: $dep")
          false
        } else {
          true
        }
      }.reduce(_ && _)
    }

  lazy val baseSparkPackageSettings: Seq[Setting[_]] = {
    Seq(
      resolvers += "Spark Packages repo" at "https://dl.bintray.com/spark-packages/maven/",
      sparkPackageNamespace := sparkPackageName.value.replace("/", "_"),
      spDistributionDirectory := target.value,
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
      // add any Python binaries when making a distribution
      mappings in spPackWithPython := (mappings in (Compile, packageBin)).value ++ {
        val pythonDirectory: Seq[File] = listFilesRecursively(baseDirectory.value / "python")
        val pythonBase = baseDirectory.value / "python"
        val pythonReqPath = baseDirectory.value / "python" / "requirements.txt"
        val pythonReq = if (pythonReqPath.exists()) Seq(pythonReqPath) else Seq()
        val pythonBinaries = pythonDirectory.filter { f => 
            f.getPath().indexOf("lib") == -1 && f.getPath().indexOf("bin") == -1 &&
            f.getPath().indexOf("doc") == -1
          }.filter(f => f.getPath().indexOf(".pyc") > -1)
        
         pythonBinaries ++ pythonReq pair relativeTo(pythonBase)
      },
      pomPostProcess in spMakeDistribution := { (node: Node) =>
        val names = sparkPackageName.value.split("/")
        require(names.length == 2,
          s"Please supply a valid sparkPackageName. sparkPackageName must be provided in " +
            s"the format: org_name/repo_name. Currently: ${sparkPackageName.value}")
        val groupId = names(0)
        val artifactId = names(1)
        val modifiedChildren = node.child.map { n =>
          if (n.label == "groupId") {
            new Elem(n.prefix, n.label, n.attributes, n.scope, true, new Text(groupId))
          } else if (n.label == "artifactId") {
            new Elem(n.prefix, n.label, n.attributes, n.scope, true, new Text(artifactId))
          } else {
            n
          }
        }
        new Elem(node.prefix, node.label, node.attributes, node.scope, true, modifiedChildren:_*)
      },
      makePomConfiguration in spMakeDistribution :=
        makePomConfiguration.value.copy(process = (pomPostProcess in spMakeDistribution).value),
      makePom in spMakeDistribution := {
        val config = (makePomConfiguration in spMakeDistribution).value
        IvyActions.makePom(ivyModule.value, config, streams.value.log)
        config.file
      },
      spMakeAssembly <<= Def.taskDyn {
        if (validatePackaging.value) {
          Def.task { assembly.value }
        } else {
          Def.task { throw new IllegalArgumentException("Illegal dependencies.") }
        }
      },
      spMakeDistribution <<= Def.taskDyn {
        if (validatePackaging.value) {
          Def.task {
            val names = sparkPackageName.value.split("/")
            require(names.length == 2,
              s"Please supply a valid sparkPackageName. sparkPackageName must be provided in " +
                s"the format: org_name/repo_name. Currently: ${sparkPackageName.value}")
            val spArtifactName = names(1) + "-" + version.value
            val jar = spPackWithPython.value
            val pom = (makePom in spMakeDistribution).value

            val zipFile = spDistributionDirectory.value / (spArtifactName + ".zip")

            IO.delete(zipFile)
            IO.zip(Seq(jar -> (spArtifactName + ".jar"), pom -> (spArtifactName + ".pom")), zipFile)

            zipFile
          }
        } else {
          Def.task { throw new IllegalArgumentException("Illegal dependencies.") }
        }
      }
    )
  }
}


