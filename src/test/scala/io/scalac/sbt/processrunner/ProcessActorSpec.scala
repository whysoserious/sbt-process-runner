package io.scalac.sbt.processrunner

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.testkit._
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import org.scalatest._

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.sys.process._

object ProcessControllerSpec {

  import io.scalac.processrunner.ProcessController._

  def immediatelyStarting(implicit system: ActorSystem): ActorRef = spawnProcessController {
    new ProcessInfo {
      override def id: String = "test-app"
      override def processBuilder: ProcessBuilder = "sleep 10"
      override def isStarted: Boolean = true
      override def applicationName: String = "sleep 10"
    }
  }

  def slowlyStarting(implicit system: ActorSystem): ActorRef = spawnProcessController {
    new ProcessInfo {
      override def id: String = "test-app"
      override def processBuilder: ProcessBuilder ="sleep 10"
      override def isStarted: Boolean = false
      override def applicationName: String = "sleep 10"
      override def startupTimeout: FiniteDuration = 10.seconds
      override def checkInterval: FiniteDuration = 1.second
    }
  }

  def notStarting(implicit system: ActorSystem): ActorRef = spawnProcessController {
    new ProcessInfo {
      override def id: String = "test-app"
      override def processBuilder: ProcessBuilder = "sleep 10"
      override def isStarted: Boolean = false
      override def applicationName: String = "sleep 10"
      override def startupTimeout: FiniteDuration = 100.milliseconds
      override def checkInterval: FiniteDuration = 30.milliseconds
    }
  }

  def immediatelyCrashing(implicit system: ActorSystem): ActorRef = spawnProcessController {
    new ProcessInfo {
      override def id: String = "test-app"
      override def processBuilder: ProcessBuilder = "sleep 1"
      override def isStarted: Boolean = false
      override def applicationName: String = "sleep 1"
    }
  }

  def crashing(timeoutInSec: Int = 1)(implicit system: ActorSystem): ActorRef = spawnProcessController {
    new ProcessInfo {
      override def id: String = "test-app"
      override def processBuilder: ProcessBuilder = s"sleep $timeoutInSec"
      override def isStarted: Boolean = true
      override def applicationName: String = s"sleep $timeoutInSec"
    }
  }

  def started(implicit system: ActorSystem): ActorRef = {
    val pc = immediatelyStarting
    pc ! Start
    awaitState(pc, Running)
    pc
  }

  def startedAndCrashed(timeoutInSec: Int = 1)(implicit system: ActorSystem): ActorRef = {
    val pc = crashing(timeoutInSec)
    pc ! Start
    awaitState(pc, Running)
    pc
  }

  def spawnProcessController(pd: ProcessInfo)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(Props(new ProcessController(pd)))
  }

  @tailrec
  private def awaitState(pc: ActorRef, expectedState: ProcessState): Unit = {
    implicit val timeout: Timeout = 100.millis
    val actualStateInfo: StateInfo = Await.result(pc ? Status, 100.millis).asInstanceOf[StateInfo]
    if (actualStateInfo.state != expectedState) {
      Thread.sleep(50)
      awaitState(pc, expectedState)
    }
  }

  val config =
    """
      |akka {
      |  loglevel = "OFF"
      |  actor {
      |    debug {
      |      receive = on
      |      autoreceive = on
      |      lifecycle = on
      |    }
      |  }
      |}
    """.stripMargin

}

class ProcessControllerSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  import ProcessControllerSpec._
  import ProcessController._

  def this() = this(
    ActorSystem(
      "ProcessControllerSpec",
      ConfigFactory.parseString(ProcessControllerSpec.config).withFallback(ConfigFactory.defaultOverrides)))

  override def afterAll() {
    TestKit.shutdownActorSystem(system, 1 second, true)
  }

  implicit val timeout: Timeout = 3.seconds

  "A ProcessController" when {
    "`Idle`" should {
      "report its state as `Idle`" in {
        immediatelyStarting ! Status
        expectMsg(StateInfo(Idle))
      }
    }
    "`Idle`" can {
      "be started and responds with `Started` message" in {
        immediatelyStarting ! Start
        expectMsg(Started)
      }
      "be started and responds with `StartupFailedWithTimeout` message if process didn't start" in {
        notStarting ! Start
        expectMsg(StartupFailedWithTimeout)
      }
      "be started and responds with `StartupFailed(_)` message if process crashed during startup" in {
        immediatelyCrashing ! Start
        expectMsgType[StartupFailed]
      }
      "not be stopped" in {
        val pc = immediatelyStarting
        pc ! Stop
        expectMsg(NotStarted)
      }
      "be started, stopped, started and stopped" in {
        val pc = immediatelyStarting
        pc ! Start
        expectMsg(Started)
        pc ! Stop
        expectMsg(Stopped(143))
        pc ! Start
        expectMsg(Started)
        pc ! Stop
        expectMsg(Stopped(143))
      }
    }
    "`Starting`" should {
      "report its state as `Starting`" in {
        val pc = slowlyStarting
        pc ! Start
        def cond: Boolean = {
          pc ! Status
          receiveOne(100 millis) == StateInfo(Starting)
        }
        awaitCond(cond, 2 seconds)
      }
    }
    "`Starting`" can {
      "be stopped" in {
        val pc = slowlyStarting
        pc ! Stop
        expectMsg(NotStarted)
        def cond: Boolean = {
          pc ! Status
          receiveOne(100 millis) == StateInfo(Idle)
        }
        awaitCond(cond, 2 seconds)
      }
      "not be started again" in {
        val pc = slowlyStarting
        pc ! Start
        def cond: Boolean = {
          pc ! Status
          receiveOne(100 millis) == StateInfo(Starting)
        }
        awaitCond(cond, 2 seconds)
        pc ! Start
        expectMsg(AlreadyStarted)
      }
    }
    "`Running`" should {
      "report its state as `Running`" in {
        started ! Status
        expectMsg(StateInfo(Running))
      }
      "report its state as `Idle` if underlying process unexpectedly finished" in {
        val pc = startedAndCrashed(1)
        def cond: Boolean = {
          pc ! Status
          receiveOne(100 millis) == StateInfo(Idle)
        }
        awaitCond(cond, 2 seconds)
      }
    }
    "`Running`" can {
      "be stopped" in {
        val pc = started
        pc ! Stop
        expectMsg(Stopped(143))
        pc ! Status
        expectMsg(StateInfo(Idle))
      }
      "be stopped if underlying process unexpectedly finished" in {
        val pc = startedAndCrashed()
        pc ! Stop
        expectMsg(Stopped(143))
        def cond: Boolean = {
          pc ! Status
          receiveOne(100 millis) == StateInfo(Idle)
        }
        awaitCond(cond, 2 seconds)
      }
      "not be started again" in {
        val pc = started
        pc ! Start
        expectMsg(AlreadyStarted)
      }
    }
  }
}
