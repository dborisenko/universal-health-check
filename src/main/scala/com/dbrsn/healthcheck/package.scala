package com.dbrsn

import cats.Id
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

package object healthcheck {
  implicit lazy val encodeHealthCheckElement: Encoder[HealthCheckElement[Id]] = deriveEncoder
  implicit lazy val encodeHealthCheck: Encoder[HealthCheck[Id]] = deriveEncoder
}
