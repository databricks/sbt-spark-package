sbt-spark-packages
==================

*Sbt Plugin for Spark Packages*

sbt-spark-packages is a plugin that aims to make the development process of Spark Packages and the use
 of Spark Packages in your applications much simpler.
 
Requirements
------------

* sbt

Setup
-----

### Manually

* Clone this repository to your local environment
* Add:
```scala
  lazy val sparkPackagesPlugin = RootProject(file("path/to/sbt-spark-packages/repo"))

  lazy val root = Project(id = "plugins", base = file(".")).dependsOn(sparkPackagesPlugin)
```
to `<your_project>/project/plugins.sbt`

Usage
-----

### Spark Package Developers

In your `build.sbt` file include the appropriate values for:

 * `sparkPackageName := "organization/my-awesome-spark-package" // the name of your Spark Package`
 
Please specify any Spark dependencies using `sparkVersion` and `sparkComponents`. For example:

 * `sparkVersion := "1.3.0" // the Spark Version your package depends on.`

 Spark Core will be included by default if no value for `sparkComponents` is supplied. You can add sparkComponents as:

 * `sparkComponents += "mllib" // creates a dependency on spark-mllib.`

 or

 * `sparkComponents ++= Seq("streaming", "sql") // adds dependencies to spark-sql and spark-streaming.`
 
You can make a zip archive ready for a release on the Spark Packages website by simply calling
`sbt spMakeDistribution`. This command will include any python files related to your package in the 
 jar inside this archive. When this jar is added to your PYTHONPATH, you will be able to use your
 Python files.

By default, the zip file will be produced in `<project>/target`, but you can 
override this by providing a value for `spDistributionDirectory` like:

`spDistributionDirectory := "Users" / "foo" / "Documents" / "bar"`

The slashes should still remain as slashes on a Windows system, don't switch them to backslashes.

### Spark Package Users

Any Spark Packages your package depends on can be added as:

 * `sparkPackageDependencies += "databricks/spark-avro:0.1" // format is spark-package-name:version`
 
We also recommend that you use `sparkVersion` and `sparkComponents` to manage your Spark dependencies.
In addition, you can use `sbt spMakeAssembly` to create an uber jar of your project.
