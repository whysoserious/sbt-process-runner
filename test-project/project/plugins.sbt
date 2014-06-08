lazy val root = project.in( file(".") ).dependsOn( sbtProcessRunnerPlugin )

lazy val sbtProcessRunnerPlugin = ProjectRef(
  uri("https://github.com/whysoserious/sbt-process-runner.git"),
  "sbt-process-runner"
)

addSbtPlugin("io.scalac" %% "sbt-process-runner" % "0.7.19-SNAPSHOT")
