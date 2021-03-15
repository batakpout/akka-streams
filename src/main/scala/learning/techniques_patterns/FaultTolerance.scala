package learning.techniques_patterns

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.Resume
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{Flow, RestartSource, Sink, Source}

import scala.util.Random

object FaultTolerance_1 extends App {

  implicit val system = ActorSystem("FT1")
  implicit val materializer = ActorMaterializer()

  /**
    * Technique 1:
    * Logging: monitoring elements completion and failures
    */

  val faultySource: Source[Int, NotUsed] = Source(1 to 10).map(e => if (e == 3) throw new RuntimeException else e)

  /**
    *
    * to log everything that goes through this source, or if we want to see what goes into our stream, just log them
    * By default element and completion signals are logged on debug level, and errors are logged on Error level.
    * This can be adjusted according to your needs by providing a custom Attributes.LogLevels attribute on the given Flow or in config
    * tag = trackingElements. So ,
    * when an operator in an akka stream throws an exception, it usually terminates the whole stream i.e all the upstream operators
    * are cancelled and all downstream operators will be informed by special message.
    */
  faultySource.log("trackingElements").to(Sink.ignore) //.run()

  /**
    * How to recover from such an exception
    * Technique 2
    * Gracefully terminating a stream:
    * It won't throw the exception, but will stop processing and finish stream after exception i.e
    * It will complete the stream with Int.MinValue
    */

  faultySource.recover {
    case _: RuntimeException => Int.MinValue // after this value is returned, stream is stopped but not crashed.
  }.log("gracefulSource").to(Sink.ignore) //.run()

  /**
    * Technique 3:
    * recover another version, which doesn't complete the stream with Int.MinValue, but instead it replaces the entire
    * upstream with another stream.
    */

  //cool
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 100)
  }).
    log("recoverWithRetries").to(Sink.ignore)//.run()

  /**
    * Technique 4: exponential backoff supervision / backoff supervisor source / backoff supervisor actor
    * first delay is 1 second, the proceed with restart with exponential time e.g power of 2 till maxBackoff, then
      restart after maxBackOff delays i.e
      if randomFactor = 0, then
      Ist restart after = 1 sec, 2nd after 2 sec, 3rd after 4, 4th after 8 seconds, now 16 but 16 < maxBackOff = 10,
      so now will restart after 10 seconds only till maxRestarts.

      if say randomFactor = 0.2, i.e 20% increase to next exponential backoff and rest of the backoffs after maxBackOff is reached.
      i.e Ist restart after = 1.22 sec, 2nd after 2.4 sec, 3rd after 4.5, 4th after 8.8 seconds, now 16.33 but 16.33 < maxBackOff = 10,
      so now will restart after 10.99 seconds only till maxRestarts.

    * So , we add a randomFactor, because if multiple actors are trying to restart through backoff strategy i.e
      if many actors failed and if now they are trying to restart, and if during restart they are trying to access database/resource,
      they chances are they will try to access database at the same time, thus creating bottleneck, so to avoid that,
      we add different randFactor so, chances are they won't restart at the same time.

      An example of where this supervisor might be used is when you may have an actor that is responsible for continuously polling on a server
      for some resource that sometimes may be down. Instead of hammering the server continuously when the resource is unavailable,
      the actor will be restarted with an exponentially increasing back off until the resource is available again
    */

  import scala.concurrent.duration._

  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 second,
    randomFactor = 0.2
   // maxRestarts = 6
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException else elem)
  })

  restartSource.log("restartBackOff").to(Sink.ignore)//.run()

  val f = Flow[Int].log("").filter(_ % 2 == 0)

  /**
    * Technique 5: Supervision Strategy on Streams but it's not always explicit.
    * So, unlike actors, akka streams operators are not automatically subjected to a supervision strategy, but they
      must be explicitly documented to support them, by default, they just fail.
    */

  val numbers = Source(1 to 20).map(n => if(n == 13) throw new RuntimeException("bad luck") else n).log("supervision") // just ignore number 13
  val supervisedNumbers = numbers.withAttributes(
    //ActorAttributes.dispatcher("mydis")
    //Attributes allows to customize: Dispatcher, Supervision, Log Levels, Buffersizes, used to customize supervision per stage.
    ActorAttributes.supervisionStrategy{
      //Resume- skips faulty element and let stream go through, stop: stop stream, restart: same as resume but clear internal state of component (e.g fold: clear states)
      case _:RuntimeException => Resume
    }
  )
  supervisedNumbers.to(Sink.ignore).run() // 13 skipped
}

object Question_Example extends App {

  //Every element that throws an exception is swallowed, and the printed result is 1,3,5… etc.
  // Is there a mechanism by which I can retry elements that caused the exception?

  /**
    * Say, I am making a call to a 3rd party REST API for each element in a stream.
    * In this case I don't want to skip a failure or start afresh the stream, but rather retry the call.
    */

  import scala.concurrent.duration._

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()


  val source = Source(1 to 10).flatMapConcat(n =>
    RestartSource.onFailuresWithBackoff(1.second, 10.seconds, 0.1, 2)(() => {
      val randomNumber = new Random().nextInt(5)
       Source.single(n).map(theActualNumber => {
        if (theActualNumber == randomNumber) throw new RuntimeException else theActualNumber
      })
    }
    ))

    source.runForeach(println)
}