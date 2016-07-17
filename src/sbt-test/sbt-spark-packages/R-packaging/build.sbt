version := "0.1"

scalaVersion := "2.10.4"

spName := "test/r-packaging"

name := "r-packaging"

organization := "awesome.test"

TaskKey[Unit]("checkZip") <<= (target) map { (target) =>
  IO.withTemporaryDirectory { dir =>
    IO.unzip(target / "r-packaging-0.1.zip", dir)
    mustExist(dir / "r-packaging-0.1.jar")
    jarContentChecks(dir / "r-packaging-0.1.jar", true)
    validatePom(dir / "r-packaging-0.1.pom", "test", "r-packaging")
  }
}

TaskKey[Unit]("checkAssemblyJar") <<= (crossTarget) map { (crossTarget) =>
  IO.withTemporaryDirectory { dir =>
    jarContentChecks(crossTarget / "r-packaging-assembly-0.1.jar", true)
  }
}

TaskKey[Unit]("checkBinJar") <<= (crossTarget) map { (crossTarget) =>
  IO.withTemporaryDirectory { dir =>
    jarContentChecks(crossTarget / "r-packaging_2.10-0.1.jar", false)
    validatePom(crossTarget / "r-packaging_2.10-0.1.pom", "awesome.test", "r-packaging_2.10")
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
def jarContentChecks(dir: File, r: Boolean): Unit = {
  IO.withTemporaryDirectory { jarDir =>
    IO.unzip(dir, jarDir)
    mustExist(jarDir / "Main.class")
    mustExist(jarDir / "R" / "pkg" / "R" / "hello.R", r)
    mustExist(jarDir / "R" / "pkg" / "DESCRIPTION", r)
    mustExist(jarDir / "R" / "pkg" / "NAMESPACE", r)
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
