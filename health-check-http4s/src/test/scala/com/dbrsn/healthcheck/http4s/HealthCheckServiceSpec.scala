package com.dbrsn.healthcheck.http4s

import cats.effect.IO
import com.dbrsn.healthcheck.HealthCheck
import com.dbrsn.healthcheck.HealthCheckStatus.{ Failure, Ok }
import org.http4s.implicits._
import org.http4s.{ Method, Request, Response, Status, Uri }
import org.scalatest.{ FlatSpec, Matchers }

class HealthCheckServiceSpec extends FlatSpec with Matchers {
  private def healthCheck(check: => HealthCheck[IO]): Response[IO] = {
    val getHW = Request[IO](Method.GET, Uri.uri("/healthcheck"))
    new HealthCheckService[IO](() => check).service.orNotFound(getHW).unsafeRunSync()
  }

  it should "return 200 OK if single checks passed" in {
    val hc = healthCheck(HealthCheck.ok("service"))
    hc.status shouldBe Status.Ok
    info(hc.as[String].unsafeRunSync())
    hc.as[String].unsafeRunSync() shouldBe """{"statuses":[{"name":"service","status":{"Ok":{}},"metadata":{}}]}"""
  }

  it should "return 200 OK if multiple checks passed" in {
    val hc = healthCheck(HealthCheck.ok("service").withCheck("other-service", Ok, Map("key" -> "value")).lift[IO])
    hc.status shouldBe Status.Ok
    info(hc.as[String].unsafeRunSync())
    hc.as[String].unsafeRunSync() shouldBe
      """{"statuses":[
        |{"name":"service","status":{"Ok":{}},"metadata":{}},
        |{"name":"other-service","status":{"Ok":{}},"metadata":{"key":"value"}}
        |]}""".stripMargin.replaceAllLiterally("\n", "")
  }

  it should "return 503 Service Unavailable if one of the single check failed" in {
    val hc = healthCheck(HealthCheck.failure("service", "ERROR"))
    hc.status shouldBe Status.ServiceUnavailable
    info(hc.as[String].unsafeRunSync())
    hc.as[String].unsafeRunSync() shouldBe """{"statuses":[{"name":"service","status":{"Failure":{"error":"ERROR"}},"metadata":{}}]}"""
  }

  it should "return 503 Service Unavailable if one of the multiple checks failed" in {
    val hc = healthCheck(HealthCheck.failure("service", "ERROR").withCheck("other-service", Ok, Map("key" -> "value")).lift[IO])
    hc.status shouldBe Status.ServiceUnavailable
    info(hc.as[String].unsafeRunSync())
    hc.as[String].unsafeRunSync() shouldBe
      """{"statuses":[
        |{"name":"service","status":{"Failure":{"error":"ERROR"}},"metadata":{}},
        |{"name":"other-service","status":{"Ok":{}},"metadata":{"key":"value"}}
        |]}""".stripMargin.replaceAllLiterally("\n", "")
  }

  it should "return 503 Service Unavailable if all of the multiple checks failed" in {
    val hc = healthCheck(HealthCheck.failure("service", "ERROR").withCheck("other-service", Failure("ERROR-2"), Map("key" -> "value")).lift[IO])
    hc.status shouldBe Status.ServiceUnavailable
    info(hc.as[String].unsafeRunSync())
    hc.as[String].unsafeRunSync() shouldBe
      """{"statuses":[
        |{"name":"service","status":{"Failure":{"error":"ERROR"}},"metadata":{}},
        |{"name":"other-service","status":{"Failure":{"error":"ERROR-2"}},"metadata":{"key":"value"}}
        |]}""".stripMargin.replaceAllLiterally("\n", "")
  }
}
