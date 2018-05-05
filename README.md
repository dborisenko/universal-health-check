[![Build Status](https://travis-ci.org/dborisenko/universal-health-check.svg?branch=master)](https://travis-ci.org/dborisenko/universal-health-check)
[![Maven Central](https://img.shields.io/maven-central/v/com.dbrsn/universal-health-check-core_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.dbrsn/universal-health-check-core_2.12)

# [Universal health-check without dependencies](http://dbrsn.com/2018-04-30-universal-health-check-without-dependencies/)

## Health Check

For simplicity reasons, we will use `circe` as a json library and `http4s` as http server. We would also like to keep ability to integrate health-checks into other http-servers (for example, in `akka-http`).

## Usage

```scala
libraryDependencies += "com.dbrsn" %% "universal-health-check-core" % "0.0.5"
libraryDependencies += "com.dbrsn" %% "universal-health-check-http4s" % "0.0.5"
```

## Model

We will start with simple Status ADT with 2 possible data types: `Ok` and `Failure`.

```scala
@JsonCodec(encodeOnly = true)
sealed abstract class HealthCheckStatus(val isOk: Boolean) {
  def isFailure: Boolean = !isOk
}

object HealthCheckStatus {

  case object Ok extends HealthCheckStatus(isOk = true)

  final case class Failure(error: String) extends HealthCheckStatus(isOk = false)

}
```

We also need another one model class for abstracting of the check itself. Let's call this component `HealthCheckElement`

```scala
final case class HealthCheckElement[F[_]](
  name: String, 
  status: F[HealthCheckStatus], 
  metadata: Map[String, String]
)
```

We use type constructor `F[_]` here. We would like to keep the check as generic as possible. So, it will represent 2 possible checks:

* The instructional check, which is not yet materialized and has to be evaluated to know the actual result of the check: `HealthCheckElement[IO]` (here I use `IO` monad from `cats-effects`).
* Already materialized check with ready to use result: `HealthCheckElement[Id]` (here I use `Id` identity type from `cats`).

And the list of all possible checks I will hold in the following structure:

```scala
final case class HealthCheck[F[_]](
  statuses: NonEmptyVector[HealthCheckElement[F]]
) {

  def withCheck(name: String, check: F[HealthCheckStatus], metadata: Map[String, String] = Map.empty): HealthCheck[F] =
    HealthCheck(statuses.append(HealthCheckElement(name, check, metadata)))

}
```

Here we also use `F[_]` with possible values `HealthCheck[IO]` for checks-instructions and `HealthCheck[Id]` for already ready checks.

## Health-check example

Here we can see some ready-to use helper methods for Postgres, Kafka or Akka health-checks. And that is how we are going to use them in the application code:

```scala
val config: Config = ConfigFactory.load()

val healthCheck: HealthCheck[IO] = HealthCheck
  .ok[IO]("App", (key: String) => Try(config.getString(key)), "metrics.tags")  // if we need to parse some `application.conf` data to metadata.
  .withActorSystemCheck(isActorSystemRunning, akka.actor.ActorSystem.Version, Some(akka.http.Version.current))  // We need to pre-fill isActorSystemRunning: Boolean flag. We also add versions of Akka Actor System and Akka.Http.
  .withPostgresCheck(db.run(sql"SELECT 1;".as[Int]))  // Health-check for Postgres will be just simple run "SELECT 1;". We use `slick` as a database driver here.
  .withKafkaProducerCheck(healthCheckProducer.send(_, _, _).map(m => m.hasOffset && m.hasTimestamp))  // Kafka Producer health-check is just sending heart-bit message to health-check topic
  .withCheck("CustomCheck", IO(isApplicationRunning).map(HealthCheckStatus(_, "Application is not running")))  // We can also add some custom check.
```

## Http4s Health Check Server

All you need to do is just to start your server:

```scala
val healthCheckServer = HealthCheckServer[IO](8080, "0.0.0.0", () => healthCheck())
healthCheckServer.run()
```

## Akka Http Health Check Server

Another option is that we can integrate healthcheck into our existed akka-http application.

```scala
val healthCheckRoute: Route = (get & path("healthcheck")) { ctx =>
  healthCheck().fold(v => complete(v), v => complete((ServiceUnavailable, v))).unsafeToFuture().flatMap(_(ctx))
}
// ...
val route: Route = handleExceptions(ApiExceptionHandler.handle)(concat(
  otherRoute,
  healthCheckRoute
))
```

## Example of json output

In happy path our health-check can return following json:

```json
{
  "statuses": [
    {
      "name": "App",
      "status": {
        "Ok": {}
      },
      "metadata": {}
    },
    {
      "name": "ActorSystem",
      "status": {
        "Ok": {}
      },
      "metadata": {
        "akka.actor.ActorSystem.Version": "2.5.11",
        "akka.http.Version.current": "10.1.1"
      }
    },
    {
      "name": "PostgresDatabase",
      "status": {
        "Ok": {}
      },
      "metadata": {}
    },
    {
      "name": "KafkaProducer",
      "status": {
        "Ok": {}
      },
      "metadata": {}
    }
  ]
}
```

In the case of failure, our health-check will return ServiceUnavailable status and will be the following:

```json
{
  "statuses": [
    {
      "name": "App",
      "status": {
        "Ok": {}
      },
      "metadata": {}
    },
    {
      "name": "ActorSystem",
      "status": {
        "Ok": {}
      },
      "metadata": {
        "akka.actor.ActorSystem.Version": "2.5.11",
        "akka.http.Version.current": "10.1.1"
      }
    },
    {
      "name": "PostgresDatabase",
      "status": {
        "Failure": {
          "error": "db.default.db - Connection is not available, request timed out after 1004ms."
        }
      },
      "metadata": {}
    },
    {
      "name": "KafkaProducer",
      "status": {
        "Failure": {
          "error": "Expiring 1 record(s) for health-check-0: 2034 ms has passed since batch creation plus linger time"
        }
      },
      "metadata": {}
    }
  ]
}
```
