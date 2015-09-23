/* ## Sbt-assembly-related settings

   This module defines settings to generate fat jars using [sbt-assembly plugin](https://github.com/softprops/assembly-sbt)
*/
package ohnosequences.sbt.nice

import sbt._
import Keys._

import sbtassembly._
import AssemblyKeys._

object AssemblySettings extends sbt.Plugin {

  /* ### Setting keys

     Classifier is the suffix appended to the artifact name
  */
  lazy val fatArtifactClassifier = settingKey[String]("Classifier of the fat jar artifact")
  /* This setting holds the url of the published fat artifact */
  lazy val fatArtifactUrl = settingKey[String]("URL of the published fat artifact")

  /* ### Settings

     Note, that these settings are not included by default. To turn them on them, add to your
     `build.sbt` `fatArtifactSettings` line (without any prefix)
  */
  lazy val fatArtifactSettings: Seq[Setting[_]] =
    addArtifact(artifact in (Compile, assembly), assembly) ++
    Seq(
      // publishing fat artifact:
      fatArtifactClassifier := "fat",
      artifact in (Compile, assembly) := (artifact in (Compile, assembly)).value.copy(
        classifier = Some(fatArtifactClassifier.value)
      ),

      // turning off tests in assembly:
      test in assembly := {},

      // mvn: "[organisation]/[module]_[scalaVersion]/[revision]/[artifact]-[revision]-[classifier].[ext]"
      // ivy: "[organisation]/[module]_[scalaVersion]/[revision]/[type]s/[artifact]-[classifier].[ext]"
      fatArtifactUrl := {
        val isMvn = publishMavenStyle.value
        val scalaV = "_"+scalaBinaryVersion.value
        val module = moduleName.value + scalaV
        val artifact = Seq(
          if (isMvn) "" else "jars/",
          module,
          if (isMvn) "-"+version.value else "",
          "-fat",
          ".jar"
        ).mkString

        Seq(
          ResolverSettings.publishS3Resolver.value.url,
          organization.value,
          module,
          version.value,
          artifact
        ).mkString("/")
      }
    )

}
