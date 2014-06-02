package jz.processrunner

import akka.actor.{ActorLogging, ActorRef, FSM}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

import scala.sys.process.Process

import scala.util.{Failure, Success}

object ProcessController {

  sealed trait ProcessState
  case object Idle extends ProcessState
  case object Starting extends ProcessState
  case object Running extends ProcessState

  sealed trait Data
  case object New extends Data
  case class Active(process: Process, exitValue: Future[Int], ref: ActorRef, timeout: Duration) extends Data

  sealed trait Message
  case object Start extends Message
  case object Stop extends Message
  case object Status extends Message
  protected case object IsStarted_? extends Message

  sealed trait Response

  sealed trait StartInfo extends Response
  case object Started extends StartInfo
  case class StartupFailed(exitValue: Int) extends StartInfo
  case object StartupFailedWithTimeout extends StartInfo
  case object AlreadyStarted extends StartInfo

  case class StateInfo(state: ProcessState) extends Response

  sealed trait StopInfo extends Response
  case class Stopped(exitValue: Int) extends StopInfo
  case object NotStarted extends StopInfo

  private val StatusCheckTimer = "status_check"

}

class ProcessController(pd: ProcessInfo)
  extends FSM[ProcessController.ProcessState, ProcessController.Data] with ActorLogging {

  import ProcessController._

  startWith(Idle, New)

  when(Idle) {
    case Event(Start, _) =>
      pd.beforeStart()
      val process = pd.processBuilder.run()
      val exitValue = getExitValue(process)
      goto(Starting) using Active(process, exitValue, sender(), pd.startupTimeout)
    case Event(Status, _) =>
      stay replying StateInfo(Idle)
    case Event(Stop, _) =>
      stay replying NotStarted
  }

  when(Starting) {
    // Process unexpectedly finished during startup phase
    case Event(IsStarted_?, Active(_, exitValue, ref, _)) if exitValue.isCompleted =>
      val ev = Await.result(exitValue, 1 second)
      log.warning(s"${pd.applicationName} has unexpectedly finished during startup. Exit value: $ev")
      ref ! StartupFailed(ev)
      goto(Idle) using New
    // Process didn't finish startup phase
    case Event(IsStarted_?, Active(_, _, ref, timeout)) if timeout < Duration.Zero =>
      log.warning(s"${pd.applicationName} didn't start in expected timeout: ${pd.startupTimeout}")
      ref ! StartupFailedWithTimeout
      goto(Idle) using New
    // Tada, Process succesfully finished startup phase
    case Event(IsStarted_?, Active(_, _, ref, _)) if pd.isStarted =>
      cancelTimer(StatusCheckTimer)
      pd.afterStart()
      log.info(s"${pd.applicationName} started")
      ref ! Started
      goto(Running)
    // Process is in startup phase
    case Event(IsStarted_?, d: Active) =>
      setTimer(StatusCheckTimer, IsStarted_?, pd.checkInterval, false)
      stay using d.copy(timeout = d.timeout - pd.checkInterval)
    case Event(Status, Active(_, exitValue, _, _)) if exitValue.isCompleted =>
      stay replying StateInfo(Idle)
    case Event(Status, _) =>
      stay replying StateInfo(Starting)
    case Event(Stop, Active(process, _, _, _)) =>
      process.destroy()
      goto(Idle) using New replying NotStarted
    case Event(Start, _) =>
      stay replying AlreadyStarted
  }

  when(Running) {
    case Event(Stop, Active(process, exitValue, _, _)) if exitValue.isCompleted =>
      goto(Idle) using New replying Stopped(Await.result(exitValue, 1 second))
    case Event(Stop, Active(process, exitValue, _, _)) =>
      pd.beforeStop()
      process.destroy()
      goto(Idle) using New replying Stopped(process.exitValue())
    case Event(Status, Active(_, exitValue, _, _)) if exitValue.isCompleted =>
      self ! Stop
      stay replying StateInfo(Idle)
    case Event(Status, _) =>
      stay replying StateInfo(Running)
    case Event(Start, _) =>
      stay replying AlreadyStarted
  }

  onTransition {
    case Idle -> Starting => self ! IsStarted_?
  }

  def getExitValue(process: Process): Future[Int] = {
    val fu = Future {
      try {
        process.exitValue()
      } catch {
        case _: Throwable => -1
      }
    }
    val thisLog = log
    fu onComplete {
      case Success(-1) => thisLog.info(s"${pd.applicationName} has finished")
      case Success(ev) => thisLog.info(s"${pd.applicationName} has finished with exit value: $ev")
      case Failure(f) =>
        log.warning(s"${pd.applicationName} has finished with error: ${f.getMessage}")
        f.printStackTrace()
    }
    fu onComplete {
      case _ => pd.afterStop()
    }
    fu
  }

}
