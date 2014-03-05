package ohnosequences.sbt

import sbt._
import Keys._
import sbt.Extracted

import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

import ohnosequences.sbt.SbtS3Resolver._
import ohnosequences.sbt.SbtGithubReleasePlugin._

import laughedelic.literator.plugin.LiteratorPlugin._

import sbtassembly._
import sbtassembly.Plugin._
import AssemblyKeys._

import com.markatta.sbttaglist._

object NiceSettingsPlugin extends sbt.Plugin {

  // Setting keys:
  lazy val isPrivate = settingKey[Boolean]("If true, publish to private S3 bucket, else to public")
  lazy val bucketSuffix = settingKey[String]("Amazon S3 bucket suffix for resolvers")
  lazy val publishBucketSuffix = settingKey[String]("Amazon S3 bucket suffix for publish-to resolver")
  lazy val publishS3Resolver = settingKey[S3Resolver]("S3Resolver which will be used in publishTo")
  lazy val fatArtifactClassifier = settingKey[String]("Classifier of the fat jar artifact")
  lazy val releaseStepByStep = settingKey[Boolean]("Defines whether release process will wait for confirmation after each step")
  
  // Just some aliases for the patterns
  val mvn = Resolver.mavenStylePatterns
  val ivy = Resolver.ivyStylePatterns

  object Nice {

    // Sets of settings:
    lazy val metainfoSettings: Seq[Setting[_]] = Seq(
        homepage := Some(url("https://github.com/"+organization.value+"/"+name.value))
      , organizationHomepage := Some(url("http://"+organization.value+".com"))
      , licenses := Seq("AGPL-V3" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))
      )

    lazy val scalaSettings: Seq[Setting[_]] = Seq(
      // this doesn't allow any conflicts in dependencies:
        conflictManager := ConflictManager.strict

      , scalaVersion := "2.10.3"
      // 2.10.x are compatible and we want to use the latest _for everything_:
      , dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value
      , dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value
      , dependencyOverrides += "org.scala-lang" % "scala-reflect" % scalaVersion.value

      , scalacOptions ++= Seq(
            "-feature"
          , "-language:higherKinds"
          , "-language:implicitConversions"
          , "-language:postfixOps"
          , "-deprecation"
          , "-unchecked"
          , "-Xlint"
          , "-target:jvm-1.7"
          )

      // full cleaning
      , cleanFiles ++= Seq(
          baseDirectory.value / "project/target"
        , baseDirectory.value / "project/project"
        , (target in (Compile, doc)).value
        )

      , commands += ApiDocsGeneration.pushApiDocsToGHPages
      )

    lazy val javaSettings: Seq[Setting[_]] = Seq(
      // to omit _2.10 suffixes:
        crossPaths := false
      // to omit scala library dependency
      , autoScalaLibrary := false

      , javacOptions ++= Seq(
          "-source", "1.7"
        , "-target", "1.7"
        , "-Xlint:unchecked"
        , "-encoding", "UTF-8"
        )
      // javadoc doesn't know about source/target 1.7
      , javacOptions in (Compile, doc) := Seq()
      )

    lazy val resolversSettings: Seq[Setting[_]] = Seq(
        bucketSuffix := {organization.value + ".com"}
      , resolvers ++= Seq ( 
          organization.value + " public maven releases"  at 
            s3("releases." + bucketSuffix.value).toHttp
        , organization.value + " public maven snapshots" at 
            s3("snapshots." + bucketSuffix.value).toHttp
        // ivy
        , Resolver.url(organization.value + " public ivy releases", 
                       url(s3("releases." + bucketSuffix.value).toHttp))(ivy)
        , Resolver.url(organization.value + " public ivy snapshots", 
                       url(s3("snapshots." + bucketSuffix.value).toHttp))(ivy)
        ) 
      )

    lazy val publishingSettings: Seq[Setting[_]] = Seq(
        isPrivate := false
      , publishMavenStyle := true
      , publishBucketSuffix := bucketSuffix.value
      , publishS3Resolver := {
          val privacy = if (isPrivate.value) "private." else ""
          val prefix = if (isSnapshot.value) "snapshots" else "releases"
          val address = privacy+prefix+"."+publishBucketSuffix.value 
          s3resolver.value(address+" S3 publishing bucket", s3(address)).
            withPatterns(if(publishMavenStyle.value) mvn else ivy)
        }
      , publishTo := {Some(publishS3Resolver.value)}
      // disable publishing docs
      , publishArtifact in (Compile, packageDoc) := false
      )

    lazy val fatArtifactSettings: Seq[Setting[_]] =
      (assemblySettings: Seq[Setting[_]]) ++ 
      addArtifact(artifact in (Compile, assembly), assembly) ++ Seq(
      // publishing also a fat artifact:
        fatArtifactClassifier := "fat"
      ,  artifact in (Compile, assembly) :=
        (artifact in (Compile, assembly)).value.copy(
           `classifier` = Some(fatArtifactClassifier.value)
        )
      , test in assembly := {}
      )

    lazy val literatorSettings = 
      Literator.settings ++ Seq[Setting[_]](
        cleanFiles ++= Literator.docsOutputDirs.value
      )

    lazy val tagListSettings: Seq[Setting[_]] = {
      import TagListPlugin._
      TagListPlugin.tagListSettings ++ Seq(
        TagListKeys.tags := Set(
          // Tag("note", TagListPlugin.Info),
          Tag("todo", TagListPlugin.Info), 
          Tag("fixme", TagListPlugin.Warn)
        ),
        compile := {
          val _ = TagListKeys.tagList.value
          (compile in Compile).value
        }
      )
    }

    def releaseTask[T](key: TaskKey[T]): ReleaseStep = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      try { 
        extracted.runAggregated(key in ref, st)
      } catch {
        case e: java.lang.Error => sys.error(e.toString)
      }
    }

    import ApiDocsGeneration._

    lazy val tempSetVersion: ReleaseStep = { st: State =>
      val v = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))._1
      st.log.info("Setting version temporarily to '" + v + "'")
      ReleaseStateTransformations.reapply(Seq(
        version in ThisBuild := v
      ), st)
    }

    // almost the same as the standard release step, but it doesn't use our modified commitMessage task
    lazy val commitNextReleaseVersion: ReleaseStep = { st: State =>
      val extracted = Project.extract(st)
      val v = st.get(versions).
        getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))._2
      val file = Project.extract(st).get(versionFile)

      val vcs = extracted.get(versionControlSystem).getOrElse(sys.error("No version control system is set!"))
      if (vcs.status.!!.trim.nonEmpty) {
        val base = vcs.baseDir
        val relativePath = IO.relativize(base, file).
          getOrElse("Version file [%s] is outside of this VCS repository with base directory [%s]!" format(file, base))
        vcs.add(relativePath) !! st.log
        vcs.commit("Setting version to '" +v+ "'") ! st.log
      }
      st
    }

    def shout(what: String, dontStop: Boolean = false): ReleaseStep = { st: State =>
      val extracted = Project.extract(st)
      st.log.info("\n"+what+"\n")
      if (extracted.get(releaseStepByStep) && !dontStop) {
        SimpleReader.readLine("Do you want to continue (y/n)? [y] ") match {
          case Some("n" | "N") => sys.error("Aborting release")
          case _ => // go on
        }
      }
      st
    }

    lazy val releaseSettings: Seq[Setting[_]] = 
      ReleasePlugin.releaseSettings ++ Seq(
        versionBump := Version.Bump.Minor
      , releaseStepByStep := true
      , tagComment  := {organization.value +"/"+ name.value +" v"+ (version in ThisBuild).value}
      // checking release notes and adding them to the commit message
      , commitMessage := {
          val log = streams.value.log
          val v = (version in ThisBuild).value
          val note: File = baseDirectory.value / "notes" / (v+".markdown")
          while (!note.exists || IO.read(note).isEmpty) {
            log.error("Release notes file "+note+"  doesn't exist or is empty!")
            SimpleReader.readLine("You can write release notes now and continue the process. Ready (y/n)? [y] ") match {
              case Some("n" | "N") => sys.error("Aborting release. No release notes.")
              case _ => // go on
            }
          }
          val text: String = IO.read(note)
          val msg = "Setting version to " +v+ ":\n\n"+ text
          log.info(msg)
          SimpleReader.readLine("Do you want to proceed with these release notes (y/n)? [y] ") match {
            case Some("n" | "N") => sys.error("Aborting release. Go write better release notes.")
            case _ => msg
          }
        }
      , releaseProcess := Seq[ReleaseStep](

          shout("[1/10] INITIAL CHECKS", dontStop = true),
          checkSnapshotDependencies,                         // no snapshot deps in release
          releaseTask(GithubRelease.checkGithubCredentials), // check that we can publish Github release

          shout("[2/10] SETTING RELEASE VERSION", dontStop = true),
          inquireVersions,                                   // ask about release version and the next one
          tempSetVersion,                                    // set the chosed version for publishing

          shout("[3/10] PACKAGING AND RUNNING TESTS"),
          releaseTask(Keys.`package`),                       // try to package the artifacts
          runTest,                                           // compile and test

          shout("[4/10] GENERATING AND COMMITING MARKDOWN DOCUMENTATION"),
          genMarkdownDocsForRelease,                         // generate literator docs and commit if needed

          shout("[5/10] GENERATING API DOCUMENTATION AND PUSHING TO GH-PAGES"),
          genApiDocsForRelease,                              // generate javadocs or scaladocs and push it to the gh-pages branch

          shout("[6/10] PUBLISHING ARTIFACTS"),
          releaseTask(publish),                              // try to publish artifacts

          shout("[7/10] COMMITTING RELEASE VERSION AND TAGGING", dontStop = true),
          setReleaseVersion,                                 // if it was ok, set the version finally
          commitReleaseVersion,                              // and commit it
          tagRelease,                                        // and make a tag

          shout("[8/10] PUBLISHING RELEASE ON GITHUB"),
          releaseTask(GithubRelease.releaseOnGithub),        // and publish notes on github

          shout("[9/10] SETTING AND COMMITTING NEXT VERSION"),
          setNextVersion,                                    // bump the version
          commitNextReleaseVersion,                          // commit it

          shout("[10/10] PUSHING COMMITS TO GITHUB", dontStop = true),
          pushChanges                                        // and push everything to github

        )
      )

    // Global combinations of settings:
    lazy val scalaProject: Seq[Setting[_]] =
      metainfoSettings ++
      scalaSettings ++
      S3Resolver.settings ++
      resolversSettings ++
      publishingSettings ++
      literatorSettings ++
      GithubRelease.defaults ++
      releaseSettings ++
      tagListSettings

    lazy val javaProject: Seq[Setting[_]] =
      scalaProject ++
      javaSettings

  }

}
