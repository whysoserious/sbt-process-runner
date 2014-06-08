## Process Runner plugin for SBT 0.13+

### Installation

#### 1. In your [plugins.sbt](https://github.com/whysoserious/sbt-process-runner/blob/master/test-project%2Fproject%2Fplugins.sbt):


```scala
lazy val root = project.in( file(".") ).dependsOn( sbtProcessRunnerPlugin )

lazy val sbtProcessRunnerPlugin = ProjectRef(
  uri("https://github.com/whysoserious/sbt-process-runner.git"),
  "sbt-process-runner"
)

addSbtPlugin("io.scalac" %% "sbt-process-runner" % "0.7.19-SNAPSHOT")
```


#### 2. Create [ProcessInfo](https://github.com/whysoserious/sbt-process-runner/blob/master/process-runner/src/main/scala/jz/io.scalac.processrunner/ProcessInfo.scala#L7-L31) object(s) in your `Build.scala`:

```scala
object Sleeper extends ProcessInfo {
  override def id: String = "sleeper"
  override def processBuilder: ProcessBuilder = "sleep 10"
  override def isStarted: Boolean = true
  override def applicationName: String = "Sleeper"
}
```

#### 3. Add ProcessRunnerPlugin.processRunnerSettings to your Projects' settings and register ProcessInfo objects:

```scala
lazy val testProject = Project(
    id = "test-project",
    base = file("."),
    // Add settings from a ProcessRunner plugin
    settings = ProcessRunnerPlugin.processRunnerSettings ++ Seq(
      scalaVersion := "2.10.4",
      scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8", "-language:postfixOps"),
      organization := "io.scalac",
      // Register ProcessInfo objects
      processInfoList in ProcessRunner := Seq(Sleeper, Listener)
    )
  )
```

You can see whole example [here](https://github.com/whysoserious/sbt-process-runner/blob/master/test-project%2Fproject%2FBuild.scala).

### Features:

#### New commands (based on a Sleeper example):

* `process-runner:status sleeper`: check status of the process - `Idle`, `Starting` or `Running`.
* `process-runner:start sleeper`: start process
* `process-runner:stop`: stop process

#### New settings:
* `process-runner:processInfoList`: Displays registered processRunners.
* `process-runner:akkaConfig`: Yes, this plugin starts its own [ActorSystem](http://doc.akka.io/docs/akka/2.3.3/general/actor-systems.html). This is its configuration.
* There are a few more settings defined in a Plugin (check [here](https://github.com/whysoserious/sbt-process-runner/blob/master/sbt-process-runner%2Fsrc%2Fmain%2Fscala%2FProcessRunnerPlugin.scala#L28-L37)) but usually you won't need to change them.

#### Flexible [ProcessInfo](https://github.com/whysoserious/sbt-process-runner/blob/master/process-runner%2Fsrc%2Fmain%2Fscala%2Fjz%2Fio.scalac.processrunner%2FProcessInfo.scala) trait:

```scala
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
```

### Example 1: _Sleeper_
Run `test-project`:
```bash
$ cd test-project
$ sbt
```

In this example we 'll use a [Sleeper](https://github.com/whysoserious/sbt-process-runner/blob/master/test-project/project/Build.scala#L23-L28). Start SBT session and try following commands:

```bash
# display available processRunners
> process-runner:processInfoList
[info] List(Sleeper, Listener)
# Check status
> process-runner:status sleeper
[info] Sleeper is idle.
[success] Total time: 0 s, completed Jun 8, 2014 6:37:43 PM
# Run it
> process-runner:start sleeper
[info] Sleeper is running
[success] Total time: 0 s, completed Jun 8, 2014 6:37:53 PM
# Check status again
> process-runner:status sleeper
[info] Sleeper is running.
[success] Total time: 0 s, completed Jun 8, 2014 6:37:55 PM
# Wait 10 seconds and check status once again
> process-runner:status sleeper
[info] Sleeper is idle.
[success] Total time: 0 s, completed Jun 8, 2014 6:38:11 PM
```

### Example 2: _Listener_

Here, we 'll start a process which listens on a port: A [Listener](https://github.com/whysoserious/sbt-process-runner/blob/master/test-project/project/Build.scala#L35-L59).

```bash
# Check status
> process-runner:status listener
[info] Listener is idle.
[success] Total time: 0 s, completed Jun 8, 2014 6:47:10 PM
# Run it
> process-runner:start listener
Starting. Try telnet 127.0.0.1 6790 in a while.
[info] Listener is running
[success] Total time: 3 s, completed Jun 8, 2014 6:47:17 PM
# Run it again. Just for fun.
> process-runner:start listener
[info] Listener was already started
[success] Total time: 0 s, completed Jun 8, 2014 6:47:55 PM
# Is it running?
> process-runner:status listener
[info] Listener is running.
[success] Total time: 0 s, completed Jun 8, 2014 6:48:16 PM
# Now, use telnet and connect to 127.0.0.1 6790
# ...
# Stop the process
> process-runner:stop listener
[info] Listener was stopped. Exit code: 143.
[success] Total time: 0 s, completed Jun 8, 2014 6:49:23 PM
# Stop the process once again. Just for fun.
> process-runner:stop listener
[info] Listener was not started.
[success] Total time: 0 s, completed Jun 8, 2014 6:49:38 PM
# Status?
> process-runner:status listener
[info] Listener is idle.
[success] Total time: 0 s, completed Jun 8, 2014 6:50:09 PM
```
