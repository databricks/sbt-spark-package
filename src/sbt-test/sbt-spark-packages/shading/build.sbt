version := "0.1"

scalaVersion := "2.10.4"

spName := "test/shading"

name := "shading"

organization := "great.test"

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("org.apache.commons.**" -> "databricks.commons.@1").inAll)

spShade := true

libraryDependencies += "org.apache.commons" % "commons-weaver-antlib" % "1.2"

assembly in spPackage := assembly.value

TaskKey[Unit]("checkZip") <<= (target) map { (target) =>
  IO.withTemporaryDirectory { dir =>
    IO.unzip(target / "shading-0.1.zip", dir)
    mustExist(dir / "shading-0.1.jar")
    jarContentChecks(dir / "shading-0.1.jar", python = true)
  }
}

TaskKey[Unit]("checkAssemblyJar") <<= (crossTarget) map { (crossTarget) =>
  IO.withTemporaryDirectory { dir =>
    jarContentChecks(crossTarget / "shading-assembly-0.1.jar", python = true)
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
