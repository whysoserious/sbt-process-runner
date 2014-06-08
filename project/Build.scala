import sbt._
import Keys._

object Build extends Build {

  lazy val commonSettings =  Seq(
    version := "0.7.19-SNAPSHOT",
    scalaVersion := "2.10.4",
    organizationHomepage := Some(url("http://scalac.io")),
    publishMavenStyle := false,
    startYear := Some(2014),
    organization := "io.scalac",
    homepage := Some(url("https://github.com/whysoserious/sbt-process-runner")),
    scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps")
  )

  object V {
    val akka = "2.3.3"
    val scalaTest = "2.1.6"
  }

  lazy val processRunner = Project(
    id = "process-runner",
    base = file("process-runner"),
    settings = commonSettings ++ Seq(      
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor"   % V.akka,
        "com.typesafe.akka" %% "akka-testkit" % V.akka      % "test",
        "org.scalatest"     %% "scalatest"    % V.scalaTest % "test"
      )
    )
  )

  lazy val sbtProcessRunner = Project(
    id = "sbt-process-runner",
    base = file("sbt-process-runner"),
    settings = commonSettings ++ Seq(
      sbtPlugin := true,
      name := "sbt-process-runner"
    )
  ).dependsOn(processRunner)

  lazy val all = Project(
    id = "all",
    base = file("."),
    settings = commonSettings ++ Seq(
      publish := {},
      publishLocal := {}
    )
  ).aggregate(processRunner, sbtProcessRunner)

}
