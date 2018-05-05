// SBT plugin adding support for source code formatting using Scalariform
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

// SBT plugin that can check Maven and Ivy repositories for dependency updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")

// Scalastyle examines your Scala code and indicates potential problems with it.
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// Flexible Scala code linting tool
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.2.1")

// A release plugin for sbt
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.8")
