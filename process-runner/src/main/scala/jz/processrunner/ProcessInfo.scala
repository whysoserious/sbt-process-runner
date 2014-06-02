package jz.processrunner

import scala.concurrent.duration._

import scala.sys.process.ProcessBuilder

trait ProcessInfo {

  def id: String

  def processBuilder: ProcessBuilder

  def isStarted: Boolean

  def applicationName: String = id

  def startupTimeout: FiniteDuration = 5.seconds

  def checkInterval: FiniteDuration = 500.milliseconds

  def beforeStart(): Unit = {}

  def afterStart(): Unit = {}

  def beforeStop(): Unit = {}

  def afterStop(): Unit = {}

  override def toString = applicationName

}
