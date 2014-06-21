resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

resolvers += Resolver.url(
  "Bintray-Resolve-whysoserious-sbt-process-runner",
  url("http://dl.bintray.com/content/whysoserious/sbt-process-runner"))(
    Resolver.ivyStylePatterns)


//bintray.Opts.resolver.repo("whysoserious", "sbt-process-runner")
//
//
//def repo(name: String, repo: String) =
//  MavenRepository(
//    "Bintray-Resolve-%s-%s" format(name, repo),
//    "http://dl.bintray.com/content/%s/%s".format(
//      name, repo))

addSbtPlugin("io.scalac" % "sbt-process-runner" % "0.8")
