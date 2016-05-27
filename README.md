sbt-spark-package [![Build Status](https://travis-ci.org/databricks/sbt-spark-package.svg)](http://travis-ci.org/databricks/sbt-spark-package)
==================

*Sbt Plugin for Spark Packages*

sbt-spark-package is a plugin that aims to make the development process of Spark Packages and the use
 of Spark Packages in your applications much simpler.

**Please upgrade to version 0.2.4 as spark-packages now supports SSL**.

Requirements
============

* sbt

Setup
=====

### The sbt way

Simply add the following to `<your_project>/project/plugins.sbt`:
```scala
  resolvers += "bintray-spark-packages" at "https://dl.bintray.com/spark-packages/maven/"
  
  addSbtPlugin("org.spark-packages" % "sbt-spark-package" % "0.2.4")
```

Usage
=====

Spark Package Developers
------------------------

In your `build.sbt` file include the appropriate values for:

 * `spName := "organization/my-awesome-spark-package" // the name of your Spark Package`

Please specify any Spark dependencies using `sparkVersion` and `sparkComponents`. For example:

 * `sparkVersion := "1.4.0" // the Spark Version your package depends on.`

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

If you want to make a release of your package against multiple Scala versions (e.g. 2.10, 2.11),
you may set `spAppendScalaVersion := true` in your build file.

In any case where you really can't specify Spark dependencies using `sparkComponents` (e.g. you have
exclusion rules) and configure them as `provided` (e.g. standalone jar for a demo), you may use
 `spIgnoreProvided := true` to properly use the `assembly` plugin.

### Registering and publishing Spark Packages

*credentials*

In order to use `spRegister` or `spPublish` to register or publish a release of your Spark Package,
you have to specify your Github credentials. You may specify your credentials through a file (recommended)
or directly in your build file like below:

```scala
credentials += Credentials(Path.userHome / ".ivy2" / ".sbtcredentials") // A file containing credentials

credentials += Credentials("Spark Packages Realm",
                           "spark-packages.org",
                           s"$GITHUB_USERNAME",
                           s"GITHUB_PERSONAL_ACCESS_TOKEN")
```

More can be found in the [sbt documentation](http://www.scala-sbt.org/0.13/docs/Publishing.html#Credentials).

Using these functions require "read:org" Github access to authenticate ownership of the repo. Documentation
to generate a Github Personal Access Token can be found
[here](https://help.github.com/articles/creating-an-access-token-for-command-line-use/).

*spRegister*

You can register your Spark Package for the first time using this plugin with the command `sbt spRegister`.
In order to register your package, you must have logged in to the Spark Packages website at least once
and supply values for the following settings in your build file:

```scala
spShortDescription := "My awesome Spark Package" // Your one line description of your package

spDescription := """My long description.
                    |Could be multiple lines long.
                    | - My package can do this,
                    | - My package can do that.""".stripMargin

credentials += // Your credentials, see above.
```

The homepage of your package is by default the web page for the Github repository. You can change the default
homepage by using:

```scala
spHomepage := // Set this if you want to specify a web page other than your github repository.
```

*spPublish*

You can publish a new release using `sbt spPublish`. The HEAD commit on your local repository will be
used as the git commit sha for your release. Therefore, please make sure that your local commit is
indeed the version you would like to make a release for, and that you have pushed that commit to the
master branch on your remote.

The required settings for `spPublish` are:

```scala
// You must have an Open Source License. Some common licenses can be found in: http://opensource.org/licenses
licenses += "Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")

// If you published your package to Maven Central for this release (must be done prior to spPublish)
spIncludeMaven := true

credentials += // Your credentials, see above.
```


Spark Package Users
-------------------

Any Spark Packages your package depends on can be added as:

 * `spDependencies += "databricks/spark-avro:0.1" // format is spark-package-name:version`

We also recommend that you use `sparkVersion` and `sparkComponents` to manage your Spark dependencies.
In addition, you can use `sbt assembly` to create an uber jar of your project.

Contributions
=============

If you encounter bugs or want to contribute, feel free to submit an issue or pull request.
