import sbt._

object Dependencies {

  object Versions {
    val scala = "2.12.6"

    val paradise = "2.1.1"

    val scalatest = "3.0.5"
    val circe = "0.9.3"
    val http4s = "0.18.10"
    val `cats-effect` = "1.0.0-RC"
  }

  lazy val paradise = "org.scalamacros" % "paradise" % Versions.paradise

  lazy val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest
  lazy val `circe-generic` = "io.circe" %% "circe-generic" % Versions.circe
  lazy val `http4s-blaze-server` = "org.http4s" %% "http4s-blaze-server" % Versions.http4s
  lazy val `http4s-circe` = "org.http4s" %% "http4s-circe" % Versions.http4s
  lazy val `http4s-dsl` = "org.http4s" %% "http4s-dsl" % Versions.http4s
  lazy val `cats-effect` = "org.typelevel" %% "cats-effect" % Versions.`cats-effect`
}
