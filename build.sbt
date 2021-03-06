sbtPlugin := true

name := "nice-sbt-settings"
organization := "ohnosequences"
description := "sbt plugin accumulating some useful and nice sbt settings"

scalaVersion := "2.12.3"
sbtVersion in Global := "1.0.2"

bucketSuffix := "era7.com"

resolvers += Resolver.jcenterRepo
resolvers += "Github-API" at "http://repo.jenkins-ci.org/public/"

addSbtPlugin("ohnosequences"     % "sbt-s3-resolver"    % "0.18.0")  // https://github.com/ohnosequences/sbt-s3-resolver
addSbtPlugin("ohnosequences"     % "sbt-github-release" % "0.5.0")   // https://github.com/ohnosequences/sbt-github-release
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"       % "0.14.5")  // https://github.com/sbt/sbt-assembly
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"        % "0.3.1")   // https://github.com/rtimush/sbt-updates
addSbtPlugin("com.markatta"      % "sbt-taglist"        % "1.4.0")   // https://github.com/johanandren/sbt-taglist
addSbtPlugin("org.wartremover"   % "sbt-wartremover"    % "2.2.1")   // https://github.com/puffnfresh/wartremover

dependencyOverrides ++= Seq(
  "commons-codec"              % "commons-codec"    % "1.9",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.1"
)
