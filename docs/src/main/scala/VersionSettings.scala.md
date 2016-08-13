## Project version settings

This plugin sets version to <last_release_tag>-<git_describe_suffix>[-SNAPSHOT (if there are uncommited changes)].


```scala
package ohnosequences.sbt.nice

import sbt._, Keys._
import scala.sys.process._
import scala.util._

case object VersionSettings extends sbt.AutoPlugin {

  override def trigger = allRequirements
  override def requires = plugins.IvyPlugin

  case object autoImport {
```

The difference between these two is that setting one will be loaded once (and may get outdated),
while the task will be rerun on each call (and will have better logging)

```scala
    lazy val gitVersion  = settingKey[Version]("Version based on git describe")
    lazy val gitVersionT = taskKey[Version]("Version based on git describe (as a task)")
    lazy val publishCarefully = taskKey[Unit]("Checks versions before publishing")
  }
  import autoImport._
```

### Settings

```scala
  override def projectSettings: Seq[Setting[_]] = Seq(
    // gitVersionT := Git.task.value.version,
    gitVersionT := Git(baseDirectory.value, streams.value.log).version,
    gitVersion  := Git(baseDirectory.value, sLog.value).version,
    version     := gitVersion.value.toString,

    publishCarefully := publishCarefullyDef.value,
    publish          := publishCarefullyDef.value
  )
```

This is a replacement for publish, that warns you if the git repo is dirty or the version is outdated

```scala
  def publishCarefullyDef: DefTask[Unit] = Def.taskDyn {
    val log = streams.value.log
    val git = Git(baseDirectory.value, log)

    val loaded = gitVersion.value
    val actual = git.version

    if (git.isDirty) Def.task {
      log.error("You have uncommited changes. Commit or stash them and reload.")
      log.error("If you want to publish a snapshot, use publishLocal. But then don't forget to clean ivy cache.")
      sys.error("Git repository is not clean.")

    } else if (loaded != actual) Def.task {
      log.error(s"Current version ${loaded} is outdated (should be ${actual}). Try to reload.")
      sys.error("Outdated version setting.")

    } else Classpaths.publishTask(Keys.publishConfiguration, Keys.deliver)
    // Def.task { publish.value }
  }
}

```




[main/scala/AssemblySettings.scala]: AssemblySettings.scala.md
[main/scala/Git.scala]: Git.scala.md
[main/scala/JavaOnlySettings.scala]: JavaOnlySettings.scala.md
[main/scala/MetadataSettings.scala]: MetadataSettings.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/release/commands.scala]: release/commands.scala.md
[main/scala/release/keys.scala]: release/keys.scala.md
[main/scala/release/parsers.scala]: release/parsers.scala.md
[main/scala/release/tasks.scala]: release/tasks.scala.md
[main/scala/ReleasePlugin.scala]: ReleasePlugin.scala.md
[main/scala/ResolverSettings.scala]: ResolverSettings.scala.md
[main/scala/ScalaSettings.scala]: ScalaSettings.scala.md
[main/scala/StatikaBundleSettings.scala]: StatikaBundleSettings.scala.md
[main/scala/Version.scala]: Version.scala.md
[main/scala/VersionSettings.scala]: VersionSettings.scala.md
[main/scala/WartRemoverSettings.scala]: WartRemoverSettings.scala.md