package com.dbrsn.healthcheck

import cats.arrow.FunctionK
import cats.data.NonEmptyVector
import cats.effect.IO
import cats.implicits._
import cats.{Applicative, Id, MonadError, ~>}
import com.dbrsn.healthcheck.HealthCheckStatus.{Failure, Ok}
import io.circe.generic.JsonCodec

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

/**
  * Status ADT with 2 possible states: `Ok` and `Failure`.
  */
@JsonCodec(encodeOnly = true)
sealed abstract class HealthCheckStatus(val isOk: Boolean) {
  def isFailure: Boolean = !isOk
}

object HealthCheckStatus {

  case object Ok extends HealthCheckStatus(isOk = true)

  final case class Failure(error: String) extends HealthCheckStatus(isOk = false)

  def apply(isOk: Boolean, error: => String): HealthCheckStatus = if (isOk) Ok else Failure(error)

  def apply(errorOrBoolean: Either[Throwable, Boolean], error: => String): HealthCheckStatus = errorOrBoolean match {
    case Left(e) => Failure(e.getMessage)
    case Right(false) => Failure(error)
    case Right(true) => Ok
  }
}

/**
  * Model class for abstracting of the check itself.
  *
  * We use type constructor `F[_]` here. We would like to keep the check as generic as possible.
  * So, it will represent 2 possible checks:
  * - The instructional check, which is not yet materialized and has to be evaluated to know the actual result
  * of the check: `HealthCheckElement[IO]` (here I use `IO` monad from `cats-effects`).
  * - Already materialized check with ready to use result: `HealthCheckElement[Id]` (here I use `Id` identity type from `cats`).
  */
final case class HealthCheckElement[F[_]](name: String, status: F[HealthCheckStatus], metadata: Map[String, String])

/**
  * The model class to hold the list of all possible checks.
  * Here we also use `F[_]` with possible values `HealthCheck[IO]` for checks-instructions and `HealthCheck[Id]` for already ready checks.
  */
final case class HealthCheck[F[_]](
  statuses: NonEmptyVector[HealthCheckElement[F]]
) {
  /**
    * Here we give an instruction how to handle success cases (`success: HealthCheck[Id] => R`) and failure cases
    * (`failure: HealthCheck[Id] => R`). We are also aware that error might happen inside this _any_ of the check.
    * That is why we have recover logic (based on `MonadError` from `cast`). All errors need to be turned to
    * `HealthCheckStatus.Failure` type of our ADT.
    */
  def fold[R](success: HealthCheck[Id] => R, failure: HealthCheck[Id] => R)(implicit A: MonadError[F, Throwable]): F[R] =
    statuses.map { v =>
      v.status.recover {
        case error => Failure(error.getMessage)
      }.map(s => HealthCheckElement[Id](v.name, s, v.metadata))
    }.sequence[F, HealthCheckElement[Id]].map { elems =>
      if (elems.exists(_.status.isFailure)) failure(HealthCheck(elems)) else success(HealthCheck(elems))
    }

  def withCheck(name: String, check: F[HealthCheckStatus], metadata: Map[String, String] = Map.empty): HealthCheck[F] =
    HealthCheck(statuses.append(HealthCheckElement(name, check, metadata)))

  def transform[G[_]](implicit NT: F ~> G): HealthCheck[G] =
    HealthCheck(statuses.map(hc => HealthCheckElement[G](hc.name, NT(hc.status), hc.metadata)))

  def headName: String = statuses.head.name

  def headMetadata: Map[String, String] = statuses.head.metadata
}

object HealthCheck {
  def ok[F[_]](name: String, metadata: Map[String, String] = Map.empty)(implicit A: Applicative[F]): HealthCheck[F] =
    HealthCheck(NonEmptyVector.one(HealthCheckElement(name, A.pure(Ok), metadata)))

  def ok[F[_]](name: String, resolver: String => Try[String], keys: String*)(implicit A: Applicative[F]): HealthCheck[F] =
    ok[F](name, keys.flatMap(k => resolver(k).toOption.map((k, _))).toMap)

  def failure[F[_]](name: String, error: String, metadata: Map[String, String] = Map.empty)(implicit A: Applicative[F]): HealthCheck[F] =
    HealthCheck(NonEmptyVector.one(HealthCheckElement(name, A.pure(Failure(error)), metadata)))

  implicit def idToApplicative[G[_]](implicit A: Applicative[G]): Id ~> G = new FunctionK[Id, G] {
    override def apply[A](fa: A): G[A] = A.pure(fa)
  }

  implicit class HealthCheckIdOps(val hc: HealthCheck[Id]) extends AnyVal {
    def lift[G[_] : Applicative]: HealthCheck[G] = hc.transform[G]
  }

  type HealthCheckKafkaTopic = String
  type HealthCheckKafkaKey = String
  type HealthCheckKafkaValue = String

  /**
    * This library can be universal without implementing any particular checks for Kafka, Postgres or anything else.
    * Our main aim was to avoid any dependencies into the common health-check module itself. But we still hold
    * that dependencies in that application modules, which uses that backing components. To integrate them we can
    * easily use non-abstract methods as parameters of universal health-check library.
    */
  implicit class HealthCheckIOOps(val hc: HealthCheck[IO]) extends AnyVal {
    def withKafkaProducerCheck(
      send: (HealthCheckKafkaTopic, HealthCheckKafkaKey, HealthCheckKafkaValue) => Future[Boolean]
    ): HealthCheck[IO] = hc.withCheck(
      name = "KafkaProducer",
      check = IO.fromFuture(IO(send("health-check", "health", "check"))).map(HealthCheckStatus(_, "Kafka Producer health-check failed"))
    )

    def withActorSystemCheck(
      isRunning: => Boolean, actorSystemVersion: String, akkaHttpVersion: Option[String] = None
    ): HealthCheck[IO] = hc.withCheck(
      name = "ActorSystem",
      check = IO(HealthCheckStatus(isRunning, "Actor System is terminated")),
      metadata = Map("akka.actor.ActorSystem.Version" -> actorSystemVersion) ++
        akkaHttpVersion.map("akka.http.Version.current" -> _)
    )

    def withPostgresCheck(
      selectOne: => Future[Vector[Int]]
    )(implicit ec: ExecutionContext): HealthCheck[IO] = hc.withCheck(
      name = "PostgresDatabase",
      check = IO.fromFuture(IO(selectOne.map(r => HealthCheckStatus(r == Vector(1), "Database is not available"))))
    )
  }

}
