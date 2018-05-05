inThisBuild(List(
  organization := "com.dbrsn",
  scalaVersion := Dependencies.Versions.scala,
  scalacOptions := Seq(
    "-deprecation", // Warning and location for usages of deprecated APIs
    "-encoding", "UTF-8",
    "-feature", // Warning and location for usages of features that should be imported explicitly
    "-unchecked", // Additional warnings where generated code depends on assumptions
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xlint", // Recommended additional warnings.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xfuture", // Turn on future language features.
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match", // Pattern match may not be typesafe.
    "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification", // Enable partial unification in type constructor inference
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:params", // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates" // Warn if a private member is unused.
  ),
  resolvers += Resolver.sbtPluginRepo("releases") // Fix for "Doc and src packages for 1.3.2 not found in repo1.maven.org" https://github.com/sbt/sbt-native-packager/issues/1063
))

lazy val macroParadiseSettings = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin(Dependencies.paradise cross CrossVersion.full)
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val `universal-health-check-core` = (project in file("universal-health-check-core"))
  .settings(macroParadiseSettings)
  .settings(publishSettings)
  .settings(wartremoverErrors ++= Warts.allBut(Wart.DefaultArguments, Wart.ImplicitParameter, Wart.Overloading))
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.`cats-effect`
    )
  )

lazy val `universal-health-check-http4s` = (project in file("universal-health-check-http4s"))
  .settings(publishSettings)
  .settings(
    wartremoverErrors in(Compile, compile) ++= Warts.allBut(Wart.DefaultArguments, Wart.ImplicitParameter, Wart.PublicInference, Wart.Nothing)
  )
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.`circe-generic`,
      Dependencies.`http4s-dsl`,
      Dependencies.`http4s-circe`,
      Dependencies.`http4s-blaze-server`,
      Dependencies.scalatest % Test
    )
  )
  .dependsOn(`universal-health-check-core`)
