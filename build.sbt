import bintray.Keys._

sbtPlugin := true

name := "sbt-process-runner"

organization := "io.scalac"

organizationName := "Scalac.io"

organizationHomepage := Some(url("http://scalac.io"))

homepage := Some(url("https://github.com/whysoserious/sbt-process-runner"))

description := "Start your own applications from SBT console."

startYear := Some(2014)

version := "0.8.1"

publishMavenStyle := false

scalaVersion := "2.10.4"

scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % "2.3.3"         ,
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test",
  "org.scalatest"     %% "scalatest"    % "2.1.7" % "test"
)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

seq(bintrayPublishSettings:_*)

repository in bintray <<= name

bintrayOrganization in bintray := None



