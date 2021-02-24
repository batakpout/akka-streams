package learning.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

/**
  * One of the fundamental features of Reactive Streams.
  * Elements flow as response to demand from Consumers.
  * So Sink issue demand to upstream and so on to source, and after that elements start flowing in the stream.
  * It's all about the synchronization of speed in between these asynchronous components.
  * Slow Consumer problem: producer producing faster than consumer, consumer is able not to process them so,
  * consumer sends a signal to upstream (to e.g flow) to slow down , if flow component is unable to comply,
  * then it itself sends signal to upstream to slow down, which will limit the production of elements
  * at the source, so flow of entire stream is slowed down.
  * If consumer sends  more demand then rate of then stream may increase again. This protocol is called Back pressure protocol
  * So backpressure is all about slowing down a fast producer in presence of a slow consumer.
  * Backpressure only makes sense in a parallel/distributed environment - otherwise,
  * everything is serial and you can only process elements at the speed of your current machine.
  */
object BackPressure_1 extends App {

  implicit val system = ActorSystem("BackPressure-1")
  implicit val materializer = ActorMaterializer()

  val fastSource: Source[Int, NotUsed] = Source(1 to 10000)
  val slowSink: Sink[Int, Future[Done]] = Sink.foreach { x =>
    //simulate a long computation
    Thread.sleep(2000)
    println(x)
  }

  //Not backpressure, running on same thread ==> Operator Fusion
  fastSource.to(slowSink).run()
}

object BackPressure_2 extends App {

  implicit val system = ActorSystem("BackPressure-2")
  implicit val materializer = ActorMaterializer()

  val fastSource: Source[Int, NotUsed] = Source(1 to 10000)
  val slowSink: Sink[Int, Future[Done]] = Sink.foreach { x =>
    //simulate a long computation
    Thread.sleep(1000)
    println(x)
  }

  //here we have an actual backpressure in place, applied by akka steams
  fastSource.async.
    to(slowSink).run()
}

object BackPressure_3 extends App {

  implicit val system = ActorSystem("BackPressure-3")
  implicit val materializer = ActorMaterializer()

  val fastSource: Source[Int, NotUsed] = Source(1 to 10000)
  val slowSink: Sink[Int, Future[Done]] = Sink.foreach { x =>
    //simulate a long computation
    Thread.sleep(1000)
    println(x)
  }
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map[Int] { x =>
    println(s"Incoming: $x")
    x + 1
  }

  /**
    * Sink sends back-pressure signals to up-stream till Flow,
    * Flow, with default buffer size = 16, processes 16 messages from Source, then, when buffer is full,
    * it sends back-pressure signal to Source to slow down till there is some demand from sink.
    * As soon as Sink will start consuming messages, Flow will process some more messages from Source till buffer is full,
    * and so on and so forth
    * Internally, the threshold is when the buffer is half empty (8 in our case).
    * This explains the batches of 8 that you see in the console.
    */
  fastSource.async.
    via(simpleFlow).async.
    to(slowSink).run
}

object BackPressure_4 extends App {

  /**
    * Akka streams components can have multiple reactions to back-pressure signals
    * reactions to backpressure (in order):
    *  - try to slow down if possible
    *  - buffer elements until there's more demand
    *  - drop down elements from the buffer if it overflows
    *  - tear down/kill the whole stream (failure)
    */


  implicit val system = ActorSystem("BackPressure-3")
  implicit val materializer = ActorMaterializer()

  val fastSource: Source[Int, NotUsed] = Source(1 to 10000)
  val slowSink: Sink[Int, Future[Done]] = Sink.foreach { x =>
    //simulate a long computation
    Thread.sleep(1000)
    println(x)
  }
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map[Int] { x =>
    println(s"Incoming: $x")
    x + 1
  }
  val bufferedFlow = simpleFlow.buffer(size = 10, overflowStrategy = OverflowStrategy.fail)
  fastSource.async.
    via(bufferedFlow).async.
    to(slowSink).run

}

object BackPressure_5 extends App {
  /**
    * We have a back pressure centric method on akka streams to manually trigger back pressure: throttling
    */
  implicit val system = ActorSystem("BackPressure-3")
  implicit val materializer = ActorMaterializer()

  val fastSource: Source[Int, NotUsed] = Source(1 to 10000)

  import scala.concurrent.duration._
  //1,, 8 : means emit only 1 element per 8 second
  fastSource.throttle(1, 8.second).to(Sink.foreach(println)).run()
}

object BackPressure_6 extends App {

  implicit val system = ActorSystem("BackPressure-3")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int](x=>{
    Thread.sleep(1000)
    println(s"Sink $x")
  })
  val flow = Flow[Int].map{x=>
    println(s"Incoming $x")
    x
  }

  val bufferedFlow = flow.buffer(5, overflowStrategy = OverflowStrategy.dropTail)
  //val bufferedFlow = flow.buffer(5, overflowStrategy = OverflowStrategy.fail) // o/p 996,997,998,999,1000, makes sense i.e when buffer is full, drop the buffer
  /**
    * OverflowStrategy.fail == when  buffer is full, stop processing any message from Source
    * OverFlowStrategy.dropBuffer= when  buffer is full, drop the buffer
    */

  /**
    * When we use async boundary on fastSource.async, then only Sink will get a buffer size of 16 (as on different thread), else
    * it won't have buffer, and processes elements from Flow buffer
    */
  fastSource.
    via(bufferedFlow).async
    .to(slowSink).run
}

object BackPressure extends App {

  implicit val system = ActorSystem("BackPressure-3")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int](x=>{
    Thread.sleep(1000)
    println(s"Sink $x")
  })
  val flow = Flow[Int].map{x=>
    println(s"Incoming $x")
    x
  }

  val bufferedFlow = flow.buffer(5, overflowStrategy = OverflowStrategy.dropTail)

  fastSource.async.
    via(bufferedFlow).async
    .to(slowSink).run
}