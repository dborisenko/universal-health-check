package com.dbrsn.healthcheck.http4s

import cats.effect.Effect
import cats.implicits._
import com.dbrsn.healthcheck.HealthCheck
import io.circe.syntax._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

import scala.language.higherKinds

class HealthCheckService[F[_]: Effect](check: () => HealthCheck[F]) extends Http4sDsl[F] {

  val service: HttpService[F] = HttpService[F] {
    case GET -> Root / "healthcheck" => check().fold(v => Ok(v.asJson), v => ServiceUnavailable(v.asJson)).flatten
  }
}
