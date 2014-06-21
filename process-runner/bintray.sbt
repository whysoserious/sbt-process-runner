import bintray.Keys._

seq(bintrayPublishSettings:_*)

repository in bintray := "sbt-process-runner"

publishMavenStyle := false

bintrayOrganization in bintray := None

sbtPlugin := true
