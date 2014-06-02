import sbt._
import Keys._

import jz.processrunner.ProcessInfo
import jz.sbt.processrunner.ProcessRunnerPlugin
import jz.sbt.processrunner.ProcessRunnerPlugin.ProcessRunner
import ProcessRunnerPlugin.Keys._

import scala.sys.process.ProcessBuilder

object Build extends Build {

  object SleepAndDie extends ProcessInfo {
    override def id: String = "sleep-and-die"
    override def processBuilder: ProcessBuilder = "sleep 10"
    override def isStarted: Boolean = true
    override def applicationName: String = "sleep 10"
  }

  lazy val testProject = Project(
    id = "test-project",
    base = file("."),
    settings = Defaults.defaultSettings ++ ProcessRunnerPlugin.processRunnerSettings ++ Seq(
      scalaVersion := "2.10.4",
      scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps"),
      organization := "jz.sbt.processrunner",
      processInfoList in ProcessRunner := Seq(SleepAndDie)
    )
  )

}
