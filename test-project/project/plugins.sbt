resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/whysoserious/sbt-process-runner/"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("io.scalac" % "sbt-process-runner" % "0.8.1")
