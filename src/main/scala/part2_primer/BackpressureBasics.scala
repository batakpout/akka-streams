package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

/**
  * One of the fundamental features of Reactive Streams
  * Elements flow as response to demand from Consumers
  * So Sink issue demand from upstream and so on to source, and after that elements start flowing
  * It's all about the synchronization of speed in between these asynchronous components.
  * Slow Consumer problem: producer producing faster than consumer is able to process them so,
  * in this case consumer sends a signal to upstream to slow down, if flow component is able to comply,
  * then it itself sends signal to upstream to slow down which will limit the production of elements
  * at the source, so flow of entire stream is slowed down, if consumer sends  more demand then rate
  * of then stream may increase again. This protocol is called Back pressure protocol
  * So backpressure is all about slowing down a fast producer in presence of a slow consumer
  */
object BackpressureBasics1 extends App {

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  fastSource.to(slowSink).run() // fusing?!
  // not backpressure

}

object BackpressureBasics2 extends App {

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val materializer = ActorMaterializer()
  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }
  fastSource.async.to(slowSink).run()
  // backpressure applied by akka streams
}

object BackpressureBasics3 extends App {

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val materializer = ActorMaterializer()
  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }
  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
    .run()
  /**
    * sink sends back-pressure signals to up stream to flow,
    * flow instead of sending them to source, buffers them internally,
    * default buffer is 16 elements, when > 16, flow sent back-pressure signal
    * to the fast source and wait for some demand from sink, as sink processed
    * some elements, flow allowed some more elements to go through it ......
    */
  //here back pressue we can see clearly
}

object BackpressureBasics4 extends App {
  /**
    * Akka streams components can have multiple reactions to back-pressure signals
    * reactions to backpressure (in order):
    *  - try to slow down if possible
    *  - buffer elements until there's more demand
    *  - drop down elements from the buffer if it overflows
    *  - tear down/kill the whole stream (failure)
    */

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val materializer = ActorMaterializer()
  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }
  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
  //    .run()

  /*
  1-16: nobody is backpressured
  17-26: flow will buffer, flow will start dropping at the next element
  26-1000: flow will always drop the oldest element
    => 991-1000 => 992 - 1001 => sink
 */

  /*
  overflow strategies:
  - drop head = oldest
  - drop tail = newest
  - drop new = exact element to be added = keeps the buffer
  - drop the entire buffer
  - backpressure signal
  - fail
 */
}

object BackpressureBasics5 extends App {
  // throttling
  implicit val system = ActorSystem("BackpressureBasics")
  implicit val materializer = ActorMaterializer()
  val fastSource = Source(1 to 1000)
  import scala.concurrent.duration._

  fastSource.throttle(10, 1 second).runWith(Sink.foreach(println))
}
