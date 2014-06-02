package jz.sbt.processrunner

import sbt._
import Keys._
import Def.Initialize
import complete.DefaultParsers._
import complete.Parser

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import akka.util.Timeout._

import com.typesafe.config.ConfigFactory

import jz.processrunner.ProcessController._
import jz.processrunner.{ProcessController, ProcessInfo}

import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

//TODO colored output
//TODO cross build
//  crossBuildingSettings
//  CrossBuilding.crossSbtVersions := Seq("0.12", "0.13")
//  addSbtPlugin("net.virtual-void" % "sbt-cross-building" % "0.8.1")
object ProcessRunnerPlugin extends Plugin {

  val ProcessRunner = config("process-runner") describedAs "Description of scope"

  object Keys {
    val processInfoList = SettingKey[Seq[ProcessInfo]]("process-info-list", "Set of ProcessInfo objects")
    val akkaConfig = SettingKey[String]("akka-config", "Configuration of ActorSystem")
    val actorSystem = SettingKey[ActorSystem]("actor-system", "The ActorSystem")
    val processInfoMap = SettingKey[Map[String, ActorRef]]("process-info-map", "Map of ProcessInfo objects")
    val messageTimeout = SettingKey[FiniteDuration]("message-timeout-in", "TODO")
    val start = inputKey[Option[StartInfo]]("Starts a process")
    val stop = inputKey[Option[StopInfo]]("Stops a process")
    val status = inputKey[Option[ProcessState]]("Displays status of a process")
  }

  import Keys._

  private lazy val startParser: Initialize[Parser[String]] = Def.setting {
    commandParser("start", processInfoMap.value.keySet)
  }

  private lazy val stopParser: Initialize[Parser[String]] = Def.setting {
    commandParser("stop", processInfoMap.value.keySet)
  }

  private lazy val statusParser: Initialize[Parser[String]] = Def.setting {
    commandParser("status", processInfoMap.value.keySet)
  }

  lazy val processRunnerSettings: Seq[Setting[_]] = inConfig(ProcessRunner)(
    Seq[Setting[_]](
      processInfoList := Seq(),
      akkaConfig := defaultActorSystemConfig,
      actorSystem := {
        ConfigFactory.load(ActorSystem.getClass.getClassLoader)
        val classLoader = ActorSystem.getClass.getClassLoader
        val config = ConfigFactory
          .parseString(akkaConfig.value)
          .withFallback(ConfigFactory.defaultReference(classLoader))
        ActorSystem("ProcessRunnerPluginSystem", config, classLoader)
      },
      processInfoMap := {
        processInfoList.value.foldLeft(Map[String, ActorRef]()) {
          case (acc, pi) if acc contains pi.id => throw new Exception(s"Duplicated id in process-info-list: ${pi.id}")
          case (acc, pi) => acc + (pi.id -> actorSystem.value.actorOf(Props(new ProcessController(pi))))
        }
      },
      messageTimeout := Duration(1, "minute"),
      start := startProcess(processInfoMap.value.get(startParser.parsed), streams.value.log)(messageTimeout.value),
      stop := stopProcess(processInfoMap.value.get(stopParser.parsed), streams.value.log)(messageTimeout.value),
      status := statusProcess(processInfoMap.value.get(statusParser.parsed), streams.value.log)(messageTimeout.value)
    )
  )

  lazy val defaultActorSystemConfig =
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

  def commandParser(command: String, keys: Set[String]): Parser[String] = {
    SpaceClass.+ ~> StringBasic.examples(keys)
  }

  def queryProcess[T](ref: Option[ActorRef])(fun: ActorRef => Future[T])
                     (implicit messageTimeout: FiniteDuration): Option[T] = { //TODO implicit?
    ref.map { actorRef =>
      implicit val timeout: Timeout = messageTimeout
      Await.result(fun(actorRef), messageTimeout)
    }
  }

  def startProcess(ref: Option[ActorRef], log: Logger)(implicit messageTimeout: FiniteDuration): Option[StartInfo] = {
    implicit val timeout: Timeout = messageTimeout
    queryProcess(ref) { actorRef =>
      (actorRef ? Start).mapTo[StartInfo] map { startInfo =>
        startInfo match {
          case Started => log.info("Application is running")
          case StartupFailed(exitValue) => log.info(s"Application failed to start. Exit value was: $exitValue.")
          case StartupFailedWithTimeout => log.info(s"Application failed to start [Timeout]. ")
          case AlreadyStarted => log.info(s"Application was already started")
        }
        startInfo
      }
    }
  }

  def stopProcess(ref: Option[ActorRef], log: Logger)(implicit messageTimeout: FiniteDuration): Option[StopInfo] = {
    implicit val timeout: Timeout = messageTimeout
    queryProcess(ref) { actorRef =>
      (actorRef ? Stop).mapTo[StopInfo] map { stopInfo =>
        stopInfo match {
          case Stopped(exitCode) => log.info(s"Application was stopped. Exit code: $exitCode.")
          case NotStarted => log.info("Application was not started.")
        }
        stopInfo
      }
    }
  }

  def statusProcess(ref: Option[ActorRef], log: Logger)(implicit messageTimeout: FiniteDuration): Option[ProcessState] = {
    implicit val timeout: Timeout = messageTimeout
    queryProcess(ref) { actorRef =>
      (actorRef ? Status).mapTo[StateInfo] map { stateInfo =>
        stateInfo match {
          case StateInfo(Idle) => log.info("Application is idle.")
          case StateInfo(Starting) => log.info("Application is starting.")
          case StateInfo(Running) => log.info("Application is running.")
        }
        stateInfo.state
      }
    }
  }

}
