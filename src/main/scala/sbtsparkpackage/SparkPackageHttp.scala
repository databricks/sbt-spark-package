package sbtsparkpackage

import scalaj.http._
import sbt._
import Keys._
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.commons.codec.binary.Base64
import SparkPackagePlugin.autoImport._
import SparkPackagePlugin._
import java.io.{BufferedInputStream, FileInputStream, InputStream}

object SparkPackageHttp {

  private val SPARK_PACKAGES_HOST = "spark-packages.org"

  private val baseLicenseMap = Array("Apache License 2.0", "BSD 3-Clause License", "BSD 2-Clause License",
    "GNU General Public License 2.0", "GNU General Public License 3.0",
    "GNU Lesser General Public License 2.1", "GNU Lesser General Public License 3.0", 
    "MIT License", "Mozilla Public License 2.0", "Eclipse Public License 1.0").zipWithIndex.toMap

  private val licenseMap = baseLicenseMap ++ Array("Apache-2.0", "BSD 3-Clause", "BSD 2-Clause", "GPL-2.0",
    "GPL-3.0", "LGPL-2.1", "LGPL-3.0", "MIT", "MPL-2.0", "EPL-1.0").zipWithIndex.toMap

  private def getAuth(credentials: Seq[Credentials]): String = {
    val creds = Credentials.forHost(credentials, SPARK_PACKAGES_HOST)
    assert(creds.nonEmpty, "Your Spark Package credentials couldn't be found. Please check if " +
      s"you set the host as `$SPARK_PACKAGES_HOST`.")
    val user = creds.head.userName
    val pswd = creds.head.passwd
    val auth: Array[Byte] = Base64.encodeBase64(s"$user:$pswd".getBytes)
    new String(auth)
  }
  
  private def getHomepage(hpKey: String, name: String): String = {
    val homepage = 
      if (hpKey.trim.length > 0) {
        hpKey
      } else {
        "https://github.com/" + name
      }
    
    // Check if homepage exists
    val connection = Http(homepage).asString
    if (connection.is2xx || connection.is3xx) {
      homepage
    } else {
      throw new IllegalArgumentException(s"Error while accessing url $homepage." +
        s"\nStatus code: ${connection.code}\nMessage: ${connection.body}\n" +
        s" Are you sure this webpage really exists?")
    }
  }
  
  /** Makes a release package request to the Spark Packages website */
  def makeReleaseCall(dist: TaskKey[File]): Def.Initialize[Task[Unit]] = Def.taskDyn {
    assert(licenses.value.nonEmpty, "Please provide a license with the key licenses in your " +
      "build.sbt file")
    assert(credentials.value.nonEmpty, "Please provide your credentials in your build.sbt file")
    val licenseId: Int = licenses.value.map { case (licenseName, _) =>
      licenseMap.getOrElse(licenseName, baseLicenseMap.size)
    }.reduce((a, b) => math.min(a, b))
    if (licenseId == baseLicenseMap.size) {
      Def.task {
        println("Your license could not be resolved. Are you sure your license name is in the " +
          "given list?")
        licenseMap.foreach(map => println(map._1))
        throw new IllegalArgumentException("Unrecognized license.")
      }
    } else {
      Def.task {
        val git_commit_sha1 = { "git rev-parse HEAD" !!}.trim()
        val archive = dist.value
        val releaseVersion = packageVersion.value
        var params = Seq("git_commit_sha1" -> git_commit_sha1, "version" -> releaseVersion,
          "license_id" -> licenseId.toString, "name" -> spName.value)
        if (spIncludeMaven.value) {
          val mrId = ivyModule.value.moduleDescriptor(streams.value.log).getModuleRevisionId
          params ++= Seq("maven_coordinate" -> s"${mrId.getOrganisation}:${mrId.getName}")
        }
        def url = Seq("http:/", SPARK_PACKAGES_HOST, "api", "submit-release").mkString("/")
        
        val fileBytes = new Array[Byte](archive.length.toInt)
        try {
          var input: InputStream = null
          try {
            var totalBytesRead = 0
            input = new BufferedInputStream(new FileInputStream(archive))
            while(totalBytesRead < fileBytes.length){
              val bytesRemaining = fileBytes.length - totalBytesRead
              val bytesRead = input.read(fileBytes, totalBytesRead, bytesRemaining)
              if (bytesRead > 0){
                totalBytesRead = totalBytesRead + bytesRead
              }
            }
          } finally {
            input.close()
          }
        }
        val auth = getAuth(credentials.value)

        val connection = Http(url).postForm(params)
          .postMulti(MultiPart("artifact_zip", archive.getName, "application/zip", 
            new String(Base64.encodeBase64(fileBytes))))
          .header("Authorization", s"Basic $auth")
          .timeout(connTimeoutMs = 2000, readTimeoutMs = 15000)
          .asString

        if (connection.is2xx) {
          println(s"SUCCESS: ${connection.body}")
        } else {
          println(s"ERROR: ${connection.body}")
        }
      }
    }
  }

  def makeRegisterCall(): Def.Initialize[Task[Unit]] = Def.task {
    assert(credentials.value.nonEmpty, "Please provide your credentials in your build.sbt file")
    val name = spName.value
    val homepage = getHomepage(spHomepage.value, name)
    val params = Seq("name" -> name, "homepage" -> homepage,
      "short_description" -> spShortDescription.value, "description" -> spDescription.value)

    def url = Seq("http:/", SPARK_PACKAGES_HOST, "api", "submit-package").mkString("/")
    val auth = getAuth(credentials.value)

    val connection = Http(url).postForm(params)
      .header("Authorization", s"Basic $auth")
      .timeout(connTimeoutMs = 2000, readTimeoutMs = 15000)
      .asString

    if (connection.is2xx) {
      println(s"SUCCESS: ${connection.body}")
    } else {
      println(s"ERROR: ${connection.body}")
    }
  }
  
}
