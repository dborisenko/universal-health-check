# [Better error handling with Coproduct of errors](http://dbrsn.com/2018-05-01-better-error-handling-with-coproduct-of-errors/)

Scala is a very reach platform. It gives you many ways to solve the same problem. Even such fundamental and basic problem as error handling. In this post I am going to describe an approach, that is becoming more common to use. It allows you to know your concrete errors, gives you flexibility to combine them and manage your effect that could potentially contain an error.

# Deliver your errors

But let's start from the beginning. Here we can list some of the ways of handling the error. Each of them has it's pros and cons and we are not going to discuss them here.

* You can use approach of Java and throw an exceptions. Java itself supports checked (exceptions that are checked at compile time — `throws` keyword as part of method signature) and unchecked (respectively, exceptions that are not checked at compiled time — this type of error might lead to random explosions in runtime) exceptions. Scala does not support this differentiation and you need to be more careful with errors. Of course, way violates referential transparency and makes function impure.

```scala
def process(): Int = throw new IllegalArgumentException()
```

* You can return any monadic-like exception wrapper like `Try`.

```scala
def process(): Try[Int] = Failure[Int](new IllegalArgumentException())
```

* Even asynchronous `Future` has built-in ability to deliver you information about your error.

```scala
def process(): Future[Int] = Future.failed[Int](new IllegalArgumentException())
```

* In some cases you don't even need to know your error. And if you just want to encode the fact that your process was finished successfully or with failure `Option[T]` is more than enough.

```scala
def process(): Option[Int] = None
```

* In other cases you need to return more complex result. For such a cases you can build your own sealed trait hierarchies of result.

```scala
sealed trait Result
final case class Ready(result: Int) extends Result
case object Pending extends Result
final case class Failed(error: Throwable) extends Result

def process(): Result = Failed(new IllegalArgumentException())
```

* Your error can be brought by your effect system (like `cats-effects`, effect system of `scalaz`, different kinds of Bifunctorial IOs, or just `EtherT[IO, E, T]`).

```scala
def processIo(): IO[Int] = IO.raiseError(new IllegalArgumentException())
def processEt(): EitherT[IO, IllegalArgumentException, Int] = EitherT(IO(Left(new IllegalArgumentException())))
``` 

* Or you can return a monad where error is one of the possible results (`Either[Throwable, T]`, `Either[String, T]`). The left side of your `Eather` can have strings, throwable errors or sealed traits hierarchies and / or classes of errors.

```scala
def process(): Either[IllegalArgumentException, Int] = Left(new IllegalArgumentException())
``` 

As you can see, there are a lot of possible approaches. All of them are valid in Scala and can be chosen based on the requirements and use cases. For simplicity reasons we will not talk about asynchronous and/or synchronous aspects of delivering your errors but just focus on the types of errors.

# Know your errors

But what if we have multiple errors? It's quite common in modern development to have a function which can go wrong in a multiple different ways. Let's say, your process can fail even to start due to `ConfigNotFoundError` or can raise an error during the processing. 

There are also few possible combinations. The most direct and straight way is just to encode your error result as `Throwable`. That is how it's done in the most cases: `Future`, `Try` or even `IO` from cats (I'm not talking about `EitherT[IO, E, T]` or `IO[Either[E, T]]` — it's a bit different way). The disadvantage of this approach is that you actually know nothing about your error in the compilation time. You have to wait in runtime and try to handle _all possible errors_ with some default scenarios if something really unexpected has happened.

Another option will be to encode your error into your types. You can always return `Either[IllegalArgumentException, R]` or `IO[Either[IllegalArgumentException, R]]` or even `EitherT[IO, IllegalArgumentException, T]`. Obviously, in this case you have concrete type of your error. You know what can go wrong — you know that you have to deal with error of concrete type IllegalArgumentException. So, you know which errors you must handle.

But what should you do if you have multiple types of this error? You still can build sealed trait hierarchies:

```scala
sealed trait Error
final case class ConfigKeyNotFoundError(key: String) extends Error
case object NumberMustBe42Error extends Error
final case class IllegalArgumentError(cause: IllegalArgumentException) extends Error
final case class NoSuchElementError(cause: NoSuchElementException) extends Error
final case class OtherError(message: String) extends Error
```

This way allows you to have quite good and readable result of your process:

```scala
def subprocessFromModule1(): Either[NoSuchElementException, Int] = Left(new NoSuchElementException)

def subprocessFromModule2(): Either[IllegalArgumentException, Int] = Left(new IllegalArgumentException)

import cats.syntax.either._
def process(): Either[Error, Int] = for {
  result1 <- subprocessFromModule1().leftMap(NoSuchElementError(_))
  result2 <- subprocessFromModule2().leftMap(IllegalArgumentError(_))
} yield result1 + result2
``` 

The disadvantage of this approach is that you have to wrap all your errors, which might happen inside of your process. If you have multiple sub-processes from different modules which return different types of errors — all of them must be wrapped.

If you don't want to wrap them then your signature can be very ugly and unmaintainable:

```scala
def process(): Either[Either[IllegalArgumentException, NoSuchElementException], Int] = for {
  result1 <- subprocessFromModule1().leftMap[Either[IllegalArgumentException, NoSuchElementException]](Right(_))
  result2 <- subprocessFromModule2().leftMap[Either[IllegalArgumentException, NoSuchElementException]](Left(_))
} yield result1 + result2
```

The type signature here is already a bit over-complicated. And if we have 3 errors it can be `Either[Either[Either[IllegalArgumentException, NumberFormatException], NoSuchElementException], Int]`, etc. You of course can play with type definitions and try to hide it. But you always have to assemble them at some point. So, your code for 3 errors will be `???.leftMap[Either[Either[IllegalArgumentException, NumberFormatException], NoSuchElementException]](v => Right(Left(_)))`. Does not look so nice, right?

But is there any other way? Yes, there is!

# Coproduct your errors

That's a pity, but Scala v2 does not support union types so far. But fortunately, `shapeless` can help us to make our life a bit easier. We are going to use `Coptoduct` — special data structure, dual to usual `Product` type (you use Product each time when you use case classes). `Coproduct` represents type-disjunction of all possible combinations. The syntax of Coproduct is quite simple:

```scala
import shapeless.{ :+:, CNil, Coproduct }

type MyError = IllegalArgumentException :+: NumberFormatException :+: NoSuchElementException :+: CNil

val error1: MyError = Coproduct[MyError](new IllegalArgumentException)
val error2: MyError = Coproduct[MyError](new NumberFormatException)
val error3: MyError = Coproduct[MyError](new NoSuchElementException)
``` 

As you can see, the Coproduct type is very descriptive and well representative. It's also very easy to assemble it. You don't need to build chains of `Right(Left(Right(Right(_))))` to build your instance. All you need is to wrap your concrete error to `Coproduct.apply` method and shapeless will take care about everything else. 

Here we use exceptions as underlying error type because instantiation of this type in JVM brings some meta-information (like stack-trace) which is helpful for error logging in some cases. 

So, this approach keeps your code concise and clean, because you don't need to create separate hierarchies of wrappers to fit your error return types. 

# Compose your errors

It's also quite easy to work with super-sets or sub-sets of your error. Let's have this example:

```scala
type StartupError = ConfigNotFoundException :+: WrongConfigException :+: CNil

def startup(): Either[StartupError, Unit] = Left(Coproduct[StartupError](new ConfigNotFoundException))
```

Here we can call `startup()` function directly. But sometimes before you are going to do your processes it's also required to run some startup code. Here we can have following example:

```scala
type ProcessingError = ErrorDuringProcessingException :+: StartupError

import cats.syntax.either._

def startedProcess(): Either[ProcessingError, Int] = Left(Coproduct[ProcessingError](new ErrorDuringProcessingException))

def process(): Either[ProcessingError, Int] = for {
  _ <- startup().leftMap(error => error.embed[ProcessingError])
  result <- startedProcess()
} yield result
```

All the runtime magic is happening here: `(error: StartupError) => error.embed[ProcessingError]`. If your result error type is sub-set of your expected type you can literally embed one type into another. And that's it. Simple. Concise.

# And finally, handle your errors

Up to now you your processing with very typefull and expressive error. You propagate your errors up to that layer, where you are ready to manage your errorfull effect and handle your errors. Let's give simple example how we can do it.

```scala
import shapeless._

object ProcessingErrorHandler extends Poly1 {
  implicit def caseConfigNotFoundException: Case.Aux[ConfigNotFoundException, (StatusCode, FailedResponse)] =
    at[ConfigNotFoundException](e => (StatusCodes.NotFound, FailedResponse(e.getMessage)))

  implicit def caseWrongConfigException: Case.Aux[WrongConfigException, (StatusCode, FailedResponse)] =
    at[WrongConfigException](e => (StatusCodes.PreconditionFailed, FailedResponse(e.getMessage)))

  implicit def caseErrorDuringProcessingException: Case.Aux[ErrorDuringProcessingException, (StatusCode, FailedResponse)] =
    at[DecisionWasNotEvenPendingException](e => (StatusCodes.BadRequest, FailedResponse(e.getMessage)))
}

val (statusCode, result) = process() match {
  case Left(error) => error.fold(ProcessingErrorHandler)
  case Right(processed) => (StatusCodes.OK, SuccessResponse(processed))
}
```

Here we have polymorphic handler `ProcessingErrorHandler` which shows how we need to handle this concrete type of error. And in the end result you can just fold it `error.fold(ProcessingErrorHandler)`. 

Pay attention that if you forget to implement one of the handler in `ProcessingErrorHandler` (let's say caseWrongConfigException) your code will be *NOT* compilable (the error will be not so much readable: `could not find implicit value for parameter folder`). So, you MUST handle all your errors. You don't have another choice, otherwise your code will not compile.

# Instead of conclusion

In this post I tried to cover all cases of good error handling practices in Scala. I also tried to explain a way which is concise and simple to use and fits to all my needs and requirements. I hope, I managed to convince the reader at least to give a try to this approach. 

All greetings for telling me about this approach go to my friends and former colleagues [Ievgen Garkusha](https://github.com/eugengarkusha) and Konstantin Spitsyn. Feel also free to check their solution [here](https://github.com/eugengarkusha/knowyourerrors).
