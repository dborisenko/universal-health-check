package com.dbrsn.healthcheck

import cats.Id
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

// scalastyle:off package.object.name
package object http4s {
  implicit lazy val encodeHealthCheckStatus: Encoder[HealthCheckStatus] = deriveEncoder[HealthCheckStatus]
  implicit lazy val encodeHealthCheckElement: Encoder[HealthCheckElement[Id]] = deriveEncoder[HealthCheckElement[Id]]
  implicit lazy val encodeHealthCheck: Encoder[HealthCheck[Id]] = deriveEncoder[HealthCheck[Id]]
}
// scalastyle:on package.object.name
