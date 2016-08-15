
import Shading._

TaskKey[Unit]("checkZip") <<= (target) map { (target) =>
  IO.withTemporaryDirectory { dir =>
    IO.unzip(target / "shading-0.1.zip", dir)
    mustExist(dir / "shading-0.1.jar")
    jarContentChecks(dir / "shading-0.1.jar", python = true)
    validatePom(dir / "shading-0.1.pom", "test", "shading", Seq(
      "commons-proxy" -> true, "commons-weaver-antlib" -> false))
  }
}

def jarContentChecks(dir: File, python: Boolean): Unit = {
  IO.withTemporaryDirectory { jarDir =>
    IO.unzip(dir, jarDir)
    mustExist(jarDir / "Main.class")
    mustExist(jarDir / "setup.py", python)
    mustExist(jarDir / "simple" / "__init__.py", python)
    mustExist(jarDir / "requirements.txt", python)
    mustExist(jarDir / "databricks" / "commons" / "weaver" / "ant" / "WeaveTask.class", true)
    mustExist(jarDir / "databricks" / "commons" / "weaver" / "ant" / "CleanTask.class", true)
    mustExist(jarDir / "org" / "apache" / "commons" / "weaver" / "ant" / "WeaveTask.class", false)
    mustExist(jarDir / "org" / "apache" / "commons" / "weaver" / "ant" / "CleanTask.class", false)
    if (python) {
      mustContain(jarDir / "requirements.txt", Seq("databricks/spark-csv==0.1"))
    }
  }
}
def validatePom(file: File, groupId: String, artifactId: String, dependencies: Seq[(String, Boolean)]): Unit = {
  import scala.xml.XML
  mustExist(file)
  val pom = XML.loadFile(file)
  val givenGroupId = (pom \ "groupId").text
  val givenArtifactId = (pom \ "artifactId").text
  assert(groupId == givenGroupId, s"groupId in pom file is wrong. $givenGroupId != $groupId")
  assert(givenArtifactId == artifactId, s"artifactId in pom file is wrong. $givenArtifactId != $artifactId")
  val pomDependencies = (pom \ "dependencies")
  dependencies.foreach { case (artifact, shouldExist) =>
    val exists = pomDependencies.exists { dependency =>
      (dependency \ "dependency" \ "artifactId").text == artifact
    }
    assert(exists == shouldExist, s"Exists: $exists, shouldExist: $shouldExist. $pomDependencies")
  }
}
def mustContain(f: File, l: Seq[String]): Unit = {
  val lines = IO.readLines(f, IO.utf8)
  if (lines != l)
    throw new Exception("file " + f + " had wrong content:\n" + lines.mkString("\n") +
      "\n*** instead of ***\n" + l.mkString("\n"))
}
def mustExist(f: File, operator: Boolean = true): Unit = {
  if (operator) {
    if (!f.exists) sys.error("file " + f + " does not exist!")
  } else {
    if (f.exists) sys.error("file " + f + " does exist!")
  }
}
