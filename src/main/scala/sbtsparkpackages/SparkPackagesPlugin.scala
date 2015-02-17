package sbtsparkpackages

import sbt._
import Keys._
import Path.relativeTo
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyKeys.{assembledMappings, assembly, assemblyPackageScala}
import scala.io.Source._
import scala.xml.{Elem, Node, NodeBuffer, NodeSeq, Null, Text, TopScope}
import java.io.{File => JavaFile}

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
    val spDist = taskKey[Unit]("Generate a zip archive for distribution on the Spark Packages website.")
    val spMakeAssembly = taskKey[File]("Generate a fat jar including all dependencies (excluding Spark).")
    val spDistDirectory = settingKey[File]("Directory to output the zip archive.")
    val spPackWithPython = taskKey[File]("Packs the Jar including Python files")

    val defaultSPSettings = Seq(
      sparkPackageDependencies := Seq(),
      sparkComponents := Seq(),
      sparkVersion := "1.2.0",
      sparkPackageName := "dummy",
      spDistDirectory := baseDirectory.value
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

  private def validateReturnSPDep(line: String): (String, String, String) = {
    val firstSplit = line.split("==")
    if (firstSplit.length != 2) {
      throw new IllegalArgumentException("Spark Package dependencies must be supplied as: " +
        s"`:package_name==:version` in spark-package-deps.txt. Found: $line")
    }
    val package_name = firstSplit(0)
    val version = firstSplit(1)
    val secondSplit = package_name.split("/")
    if (secondSplit.length != 2) {
      throw new IllegalArgumentException("Spark Package names must be supplied as: " +
        s"`:repo_owner_name/:repo_name` in spark-package-deps.txt. Found: $package_name")
    }
    (secondSplit(0), secondSplit(1), version)
  }

  private def getPythonSparkPackageDeps(parent: Node, orgDeps: Option[Node]): NodeSeq = {
    val dependencies = orgDeps.orNull
    if (dependencies != null) {
      val pythonDeps = new File("python" + JavaFile.separator + "spark-package-deps.txt")
      if (pythonDeps.exists) {
        val buffer = new NodeBuffer
        for (line <- fromFile(pythonDeps).getLines) {
          val strippedLine = line.trim()
          if (strippedLine.length > 0 && !strippedLine.startsWith("#")) {
            val (groupId, artifactId, version) = validateReturnSPDep(strippedLine)
            def depExists: Boolean = {
              dependencies.child.foreach { dep =>
                if ((dep \ "groupId").text == groupId && (dep \ "artifactId").text == artifactId &&
                  (dep \ "version").text == version) {
                  return true
                }
              }
              false
            }
            if (!depExists) {
              buffer.append(new Elem(null, "dependency", Null, TopScope, false,
                new Elem(null, "groupId", Null, TopScope, false, new Text(groupId)),
                new Elem(null, "artifactId", Null, TopScope, false, new Text(artifactId)),
                new Elem(null, "version", Null, TopScope, false, new Text(version)))
              )
            }
          }
        }
        dependencies.child ++ buffer.result()
      } else {
        dependencies.child
      }
    } else {
      Seq[Node]()
    }
  }

  lazy val baseSparkPackageSettings: Seq[Setting[_]] = {
    Seq(
      resolvers += "Spark Packages Repo" at "https://dl.bintray.com/spark-packages/maven/",
      sparkPackageNamespace := sparkPackageName.value.replace("/", "_"),
      spDistDirectory := target.value,
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
      pomPostProcess in spDist := { (node: Node) =>
        val names = sparkPackageName.value.split("/")
        require(names.length == 2,
          s"Please supply a valid sparkPackageName. sparkPackageName must be provided in " +
            s"the format: org_name/repo_name. Currently: ${sparkPackageName.value}")
        val groupId = names(0)
        val artifactId = names(1)
        val dependencies = (node \ "dependencies").headOption
        val modifiedChildren = node.child.map { n =>
          if (n.label == "groupId") {
            new Elem(n.prefix, n.label, n.attributes, n.scope, true, new Text(groupId))
          } else if (n.label == "artifactId") {
            new Elem(n.prefix, n.label, n.attributes, n.scope, true, new Text(artifactId))
          } else {
            n
          }
        }.filter(_.label != "dependencies")
        val allDeps = getPythonSparkPackageDeps(node, dependencies)
        val addedDeps =
          if (allDeps.length > 0) {
            modifiedChildren ++ new Elem(null, "dependencies", Null, TopScope,
              true, allDeps:_*)
          } else {
            modifiedChildren
          }
        new Elem(node.prefix, node.label, node.attributes, node.scope, true, addedDeps:_*)
      },
      makePomConfiguration in spDist :=
        makePomConfiguration.value.copy(process = (pomPostProcess in spDist).value),
      makePom in spDist := {
        val config = (makePomConfiguration in spDist).value
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
      spDist <<= Def.taskDyn {
        if (validatePackaging.value) {
          Def.task {
            val names = sparkPackageName.value.split("/")
            require(names.length == 2,
              s"Please supply a valid sparkPackageName. sparkPackageName must be provided in " +
                s"the format: org_name/repo_name. Currently: ${sparkPackageName.value}")
            val spArtifactName = names(1) + "-" + version.value
            val jar = spPackWithPython.value
            val pom = (makePom in spDist).value

            val zipFile = spDistDirectory.value / (spArtifactName + ".zip")

            IO.delete(zipFile)
            IO.zip(Seq(jar -> (spArtifactName + ".jar"), pom -> (spArtifactName + ".pom")), zipFile)

            zipFile
          }
        } else {
          Def.task { throw new IllegalArgumentException("Illegal dependencies.") }
        }
      },
      initialCommands in console :=
        """
          |import org.apache.spark.SparkContext
          |import org.apache.spark.SparkContext._
          |import org.apache.spark.SparkConf
          |val conf = new SparkConf()
          |      .setMaster("local")
          |      .setAppName("Sbt console + Spark!")
          |val sc = new SparkContext(conf)
          |println("Created spark context as sc.")
        """.stripMargin,
      cleanupCommands in console :=
        """
          |sc.stop()
        """.stripMargin
    )
  }
}


