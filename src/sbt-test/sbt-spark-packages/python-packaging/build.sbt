version := "0.1"

scalaVersion := "2.10.4"

spName := "test/python-packaging"

name := "python-packaging"

organization := "awesome.test"

TaskKey[Unit]("checkZip") <<= (target) map { (target) =>
  IO.withTemporaryDirectory { dir =>
    IO.unzip(target / "python-packaging-0.1.zip", dir)
    mustExist(dir / "python-packaging-0.1.jar")
    jarContentChecks(dir / "python-packaging-0.1.jar", true)
    validatePom(dir / "python-packaging-0.1.pom", "test", "python-packaging")
  }
}

TaskKey[Unit]("checkAssemblyJar") <<= (crossTarget) map { (crossTarget) =>
  IO.withTemporaryDirectory { dir =>
    jarContentChecks(crossTarget / "python-packaging-assembly-0.1.jar", true)
  }
}

TaskKey[Unit]("checkBinJar") <<= (crossTarget) map { (crossTarget) =>
  IO.withTemporaryDirectory { dir =>
    jarContentChecks(crossTarget / "python-packaging_2.10-0.1.jar", false)
    validatePom(crossTarget / "python-packaging_2.10-0.1.pom", "awesome.test", "python-packaging_2.10")
  }
}

def validatePom(file: File, groupId: String, artifactId: String): Unit = {
  import scala.xml.XML
  mustExist(file)
  val pom = XML.loadFile(file)
  val givenGroupId = (pom \ "groupId").text
  val givenArtifactId = (pom \ "artifactId").text
  assert(groupId == givenGroupId, s"groupId in pom file is wrong. $givenGroupId != $groupId")
  assert(givenArtifactId == artifactId, s"artifactId in pom file is wrong. $givenArtifactId != $artifactId")
}
def jarContentChecks(dir: File, python: Boolean): Unit = {
  IO.withTemporaryDirectory { jarDir =>
    IO.unzip(dir, jarDir)
    jarDir.listFiles().foreach(println)
    mustExist(jarDir / "Main.class")
    mustExist(jarDir / "setup.py", python)
    mustExist(jarDir / "simple" / "__init__.py", python)
    mustExist(jarDir / "requirements.txt", python)
    if (python) {
      mustContain(jarDir / "requirements.txt", Seq("databricks/spark-csv==0.1"))
    }
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
