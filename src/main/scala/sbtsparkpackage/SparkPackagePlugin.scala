package sbtsparkpackage

import java.util.Locale

import sbt.Package.ManifestAttributes
import sbt._
import Keys._
import Path.relativeTo
import sbtassembly.AssemblyPlugin
import sbtassembly.MappingSet
import sbtassembly.AssemblyKeys.{assembledMappings, assembly}
import scala.io.Source._
import scala.xml.{Elem, Node, NodeBuffer, NodeSeq, Null, Text, TopScope}
import java.io.{File => JavaFile}
import SparkPackageHttp._

object SparkPackagePlugin extends AutoPlugin {

  object autoImport {

    // Important Keys
    val sparkVersion = settingKey[String]("The version of Spark to build against.")
    val sparkComponents = settingKey[Seq[String]](
      "The components of Spark this package depends on. e.g. mllib, sql, graphx, streaming. Spark " +
        "Core is included by default if this key is not set.")
    @deprecated("Use spName", "0.2.0")
    lazy val sparkPackageName = spName
    lazy val spName = taskKey[String]("The name of the Spark Package")
    @deprecated("Use spDependencies", "0.2.0")
    lazy val sparkPackageDependencies = spDependencies
    lazy val spDependencies = settingKey[Seq[String]]("The Spark Package dependencies.")

    // Release packaging related
    val spDist = taskKey[File]("Generate a zip archive for distribution on the Spark Packages website.")
    val spDistDirectory = settingKey[File]("Directory to output the zip archive.")
    val spPackage = taskKey[File]("Packs the Jar including Python files")
    val spMakePom = taskKey[File]("Generates the modified pom file")
    val spShade = settingKey[Boolean]("Whether to use a shaded assembly jar as the source.")
    val spPublishLocal = taskKey[Unit]("Publish your package to local ivy repository")
    val spAppendScalaVersion = settingKey[Boolean]("Whether to append the Scala version to the " +
      "release version")

    // Package Registeration Related
    val spRegister = taskKey[Unit]("Register your package to Spark Packages. Requires the user to have logged " +
      "in to the Spark Packages website.")
    val spShortDescription = taskKey[String]("The one line description of your Spark Package")
    val spDescription = taskKey[String]("The long description of your Spark Package")
    val spHomepage = settingKey[String]("The homepage for your Spark Package. Will be the github repo by default.")

    // Release Publishing Related
    val spPublish = taskKey[Unit]("Publish a release to the Spark Packages repository")
    val spIncludeMaven = settingKey[Boolean]("Include your maven coordinate with your release. The artifacts must " +
      "be published on Maven Central before running spPublish.")

    // Misc, worst-case keys
    val spIgnoreProvided = settingKey[Boolean]("Whether to ignore if Spark dependencies have been configured" +
      "as \"provided\" or not.")

    val defaultSPSettings = Seq(
      sparkVersion := "1.4.0",
      sparkComponents := Seq.empty[String],
      spName := sys.error("Please set your Spark Package name using spName."),
      spDependencies := Seq.empty[String],
      spShortDescription := sys.error("Please set a short description for your package."),
      spDescription := sys.error("Please set a long description for your package."),
      spHomepage := "",
      spIgnoreProvided := false,
      spAppendScalaVersion := false,
      spIncludeMaven := false,
      spDistDirectory := baseDirectory.value
    )
  }

  import autoImport._

  override def requires = plugins.JvmPlugin && AssemblyPlugin

  override def trigger = allRequirements

  def listFilesRecursively(dir: File): Seq[File] = {
    val list = IO.listFiles(dir)
    list.filter(_.isFile) ++ list.filter(_.isDirectory).flatMap(listFilesRecursively)
  }

  override lazy val buildSettings: Seq[Setting[_]] = defaultSPSettings

  override lazy val projectSettings: Seq[Setting[_]] =
    Defaults.packageTaskSettings(spPackage, mappings in(Compile, spPackage)) ++
      baseSparkPackageSettings ++ spPublishingSettings

  // spark-streaming-kafka and spark-ganglia are not included in the spark-assembly, therefore it
  // should be okay to not mark those as provided.
  val nonProvided = Seq("spark-streaming-", "spark-ganglia")

  val validatePackaging = Def.task {
    // Make sure Spark configuration is "provided"
    libraryDependencies.value.map { dep =>
      if (dep.organization == "org.apache.spark" && dep.configurations != Some("provided") &&
        !spIgnoreProvided.value) {
        var ignore = false
        for (comp <- nonProvided) {
          if (dep.name.indexOf(comp) > -1) {
             ignore = true
          }
        }
        if (ignore) {
          true
        } else {
          sys.error("Please add any Spark dependencies by supplying the sparkVersion " +
            s"and sparkComponents. Please remove: $dep")
          false
        }
      } else if (dep.organization == "org.apache.spark" && dep.revision != sparkVersion.value) {
        sys.error("Please add any Spark dependencies by supplying the sparkVersion " +
          s"and sparkComponents. Please remove: $dep")
        false
      } else {
        true
      }
    }.reduce(_ && _)
  }

  def normalizeName(s: String) = s.toLowerCase(Locale.ENGLISH).replaceAll( """\W+""", "-")

  def validateReturnSPDep(line: String): (String, String, String) = {
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

  def getPythonSparkPackageDeps(parent: Node, orgDeps: Option[Node]): NodeSeq = {
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

  val listPythonBinaries: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn {
    if (validatePackaging.value) {
      Def.task {
        val pythonDirectory: Seq[File] = listFilesRecursively(baseDirectory.value / "python")
        val pythonBase = baseDirectory.value / "python"
        val pythonReqPath = baseDirectory.value / "python" / "requirements.txt"
        // Compile the python files
        if (pythonDirectory.length > 0) {
          s"python -m compileall ${(baseDirectory.value / "python")}" !
        }
        val pythonReq = if (pythonReqPath.exists()) Seq(pythonReqPath) else Seq()
        val pythonBinaries = pythonDirectory.filter { f =>
          f.getPath().indexOf("lib") == -1 && f.getPath().indexOf("bin") == -1 &&
            f.getPath().indexOf("doc") == -1
        }.filter(f => f.getPath().endsWith(".py"))

        pythonBinaries ++ pythonReq pair relativeTo(pythonBase)
      }
    } else {
      Def.task {
        throw new IllegalArgumentException("Illegal dependencies.")
      }
    }
  }

  val listRSource: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn {
    if (validatePackaging.value) {
      Def.task(listFilesRecursively(baseDirectory.value / "R") pair relativeTo(baseDirectory.value))
    } else {
      Def.task(throw new IllegalArgumentException("Illegal dependencies."))
    }
  }

  val packageVersion = Def.setting {
    if (spAppendScalaVersion.value) {
      version.value + "-s_" + CrossVersion.binaryScalaVersion(scalaVersion.value)
    } else {
      version.value
    }
  }

  def spArtifactName(sp: String, version: String, ext: String=".jar"): String = {
    spBaseArtifactName(sp, version) + "." + ext
  }

  def spBaseArtifactName(sp: String, version: String): String = {
    val names = sp.split("/")
    require(names.length == 2,
      s"Please supply a valid Spark Package name. spName must be provided in " +
        s"the format: org_name/repo_name. Currently: $sp")
    require(names(0) != "abcdefghi" && names(1) != "zyxwvut",
      s"Please supply a Spark Package name. spName must be provided in " +
        s"the format: org_name/repo_name.")
    normalizeName(names(1)) + "-" + version
  }

  def spPackageKeys = Seq(spPackage)
  lazy val spPackages: Seq[TaskKey[File]] =
    for(task <- spPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
  lazy val spArtifactTasks: Seq[TaskKey[File]] = spMakePom +: spPackages

  def spDeliverTask(config: TaskKey[DeliverConfiguration]) =
    (ivyModule in spDist, config, update, streams) map { (module, config, _, s) => IvyActions.deliver(module, config, s.log) }
  def spPublishTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]) =
    (ivyModule in spDist, config, streams) map { (module, config, s) =>
      IvyActions.publish(module, config, s.log)
    } tag(Tags.Publish, Tags.Network)

  private def getInitialCommandsForConsole: Def.Initialize[String] = Def.settingDyn {
    val base = """ println("Welcome to\n" +
      |"      ____              __\n" +
      |"     / __/__  ___ _____/ /__\n" +
      |"    _\\ \\/ _ \\/ _ `/ __/  '_/\n" +
      |"   /___/ .__/\\_,_/_/ /_/\\_\\   version \"%s\"\n" +
      |"      /_/\n" +
      |"Using Scala \"%s\"\n")
      |
      |import org.apache.spark.SparkContext._
      |
      |val sc = {
      |  val conf = new org.apache.spark.SparkConf()
      |    .setMaster("local")
      |    .setAppName("Sbt console + Spark!")
      |  new org.apache.spark.SparkContext(conf)
      |}
      |println("Created spark context as sc.")
    """.format(sparkVersion.value, scalaVersion.value).stripMargin
    if (libraryDependencies.value.map(_.name.contains("spark-sql")).reduce(_ || _)) {
      Def.setting {
        base +
          """val sqlContext = {
            |  val _sqlContext = new org.apache.spark.sql.SQLContext(sc)
            |  println("SQL context available as sqlContext.")
            |  _sqlContext
            |}
            |import sqlContext.implicits._
            |import sqlContext.sql
            |import org.apache.spark.sql.functions._
          """.stripMargin
      }
    } else {
      Def.setting(base)
    }
  }

  private lazy val spJar = Def.taskDyn {
    if (spShade.value) {
      Def.task((assembly in spPackage).value)
    } else {
      Def.task(spPackage.value)
    }
  }

  lazy val baseSparkPackageSettings: Seq[Setting[_]] = {
    Seq(
      resolvers += "Spark Packages Repo" at "https://dl.bintray.com/spark-packages/maven/",
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
      libraryDependencies ++= spDependencies.value.map { sparkPackage =>
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
      mappings in (Compile, spPackage) := {
        (mappings in (Compile, packageBin)).value ++ listPythonBinaries.value ++ listRSource.value
      },
      assembledMappings in assembly := (assembledMappings in assembly).value ++ Seq(
        new MappingSet(None, listPythonBinaries.value.toVector),
        new MappingSet(None, listRSource.value.toVector)),
      packageOptions += ManifestAttributes(
        ("Spark-HasRPackage", (listRSource.value.length > 0).toString)),
      spMakePom := {
        val config = makePomConfiguration.value
        IvyActions.makePom((ivyModule in spDist).value, config, streams.value.log)
        config.file
      },
      spDist := {
        val spArtifactName = spBaseArtifactName(spName.value, packageVersion.value)
        val jar = spJar.value
        val pom = spMakePom.value

        val zipFile = spDistDirectory.value / (spArtifactName + ".zip")

        IO.delete(zipFile)
        IO.zip(Seq(jar -> (spArtifactName + ".jar"), pom -> (spArtifactName + ".pom")), zipFile)
        println(s"\nZip File created at: $zipFile\n")
        zipFile
      },
      spPublish <<= makeReleaseCall(spDist),
      spRegister <<= makeRegisterCall,
      initialCommands in console := getInitialCommandsForConsole.value,
      cleanupCommands in console := "sc.stop()",
      spShade := false
    )
  }

  def spProjectID = Def.task {
    val names = spName.value.split("/")
    require(names.length == 2,
      s"Please supply a valid Spark Package name. spName must be provided in " +
        s"the format: org_name/repo_name. Currently: ${spName.value}")
    require(names(0) != "abcdefghi" && names(1) != "zyxwvut",
      s"Please supply a Spark Package name. spName must be provided in " +
        s"the format: org_name/repo_name.")

    val base = ModuleID(names(0), normalizeName(names(1)), packageVersion.value).artifacts(artifacts.value : _*)
    apiURL.value match {
      case Some(u) if autoAPIMappings.value => base.extra(CustomPomParser.ApiURLKey -> u.toExternalForm)
      case _ => base
    }
  }

  lazy val spPublishingSettings: Seq[Setting[_]] = Seq(
    publishLocalConfiguration in spPublishLocal := Classpaths.publishConfig(
      packagedArtifacts.in(spPublishLocal).value, Some(deliverLocal.value),
      checksums.in(publishLocal).value, logging = ivyLoggingLevel.value),
    packagedArtifacts in spPublishLocal <<= Classpaths.packaged(spArtifactTasks),
    packagedArtifact in spMakePom := ((artifact in spMakePom).value, spMakePom.value),
    artifacts <<= Classpaths.artifactDefs(spArtifactTasks),
    deliverLocal in spPublishLocal <<= spDeliverTask(deliverLocalConfiguration),
    spPublishLocal <<= spPublishTask(publishLocalConfiguration in spPublishLocal, deliverLocal in spPublishLocal),
    moduleSettings in spPublishLocal := new InlineConfiguration(spProjectID.value,
      projectInfo.value, allDependencies.value, dependencyOverrides.value, ivyXML.value,
      ivyConfigurations.value, defaultConfiguration.value, ivyScala.value, ivyValidate.value,
      conflictManager.value),
    ivyModule in spDist := { val is = ivySbt.value; new is.Module((moduleSettings in spPublishLocal).value) }
  )
}
