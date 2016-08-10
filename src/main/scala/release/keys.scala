package ohnosequences.sbt.nice.release

import sbt._
// import ohnosequences.sbt.nice._

case object keys {

  lazy val ReleaseTest = config("releaseTest").extend(Test)

  lazy val releaseOnlyTestTag = settingKey[String]("Full name of the release-only tests tag")
  lazy val publishFatArtifact = settingKey[Boolean]("Determines whether publish in release will also upload fat-jar")

  lazy val checkGit = inputKey[Unit]("Checks git repository and its remote")
  lazy val checkReleaseNotes = inputKey[Either[File, File]]("Checks precense of release notes and returns its file")
  lazy val snapshotDependencies = taskKey[Seq[ModuleID]]("Returns the list of dependencies with changing/snapshot versions")
  lazy val checkDependencies = taskKey[Unit]("Checks that there are no snapshot or outdated dependencies")

  lazy val publishApiDocs = taskKey[Unit]("Publishes API docs to the gh-pages branch of the repo")

  lazy val prepareRelease = inputKey[Unit]("Runs all pre-release checks sequentially")
  lazy val    makeRelease = inputKey[Unit]("Publishes the release")
}