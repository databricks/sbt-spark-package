sbt-spark-package
==================

*Sbt Plugin for Spark Packages*

sbt-spark-package is a plugin that aims to make the development process of Spark Packages and the use
 of Spark Packages in your applications much simpler.
 
Requirements
------------

* sbt

Setup
-----

### The sbt way

Simply add the following to `<your_project>/project/plugins.sbt`:
```scala
  resolvers += "Spark Package Main Repo" at "https://dl.bintray.com/spark-packages/maven"

  addSbtPlugin("org.spark-packages" % "sbt-spark-package" % "0.1")
```

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

 * `sparkComponents ++= Seq("streaming", "sql")`
 
You can make a zip archive ready for a release on the Spark Packages website by simply calling
`sbt spDist`. This command will include any python files related to your package in the
 jar inside this archive. When this jar is added to your PYTHONPATH, you will be able to use your
 Python files.

By default, the zip file will be produced in `<project>/target`, but you can 
override this by providing a value for `spDistDirectory` like:

`spDistDirectory := "Users" / "foo" / "Documents" / "bar"`

The slashes should still remain as slashes on a Windows system, don't switch them to backslashes.

You may publish your package locally for testing with `sbt spPublishLocal`.

In addition, `sbt console` will create you a Spark Context for testing your code like the spark-shell.

### Spark Package Users

Any Spark Packages your package depends on can be added as:

 * `sparkPackageDependencies += "databricks/spark-avro:0.1" // format is spark-package-name:version`
 
We also recommend that you use `sparkVersion` and `sparkComponents` to manage your Spark dependencies.
In addition, you can use `sbt assembly` to create an uber jar of your project.

Contributions
-------------

If you encounter bugs or want to contribute, feel free to submit an issue or pull request.
