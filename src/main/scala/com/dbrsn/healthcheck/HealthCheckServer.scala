package com.dbrsn.healthcheck

import cats.effect.Effect
import fs2.StreamApp
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

final case class HttpServerConfig(
  host: String,
  port: Int
)

abstract class HealthCheckServer[F[_] : Effect](
  port: Int = 8080,
  host: String = "0.0.0.0",
  check: () => HealthCheck[F]
)(implicit ec: ExecutionContext) extends StreamApp[F] {
  def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, StreamApp.ExitCode] =
    new HealthCheckStream(port, host, check).stream

  def run(): Unit = main(Array.empty)
}

object HealthCheckServer {
  def apply[F[_] : Effect](
    config: HttpServerConfig, check: () => HealthCheck[F]
  )(implicit ec: ExecutionContext): HealthCheckServer[F] =
    new HealthCheckServer[F](config.port, config.host, check) {}
}

class HealthCheckStream[F[_] : Effect](port: Int, host: String, check: () => HealthCheck[F]) {

  private val healthCheckService: HttpService[F] = new HealthCheckService[F](check).service

  def stream(implicit ec: ExecutionContext): fs2.Stream[F, StreamApp.ExitCode] = BlazeBuilder[F]
    .bindHttp(port, host).mountService(healthCheckService, "/").serve
}

