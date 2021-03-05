package learning.graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future

/**
  * Expose materialized values in components  build from Graph DSL
  */

object GraphMaterializedValues_1 extends App {

  implicit val system = ActorSystem("GraphMaterializedValues-1")
  implicit val materializer = ActorMaterializer()

  /**
    * A composite sink: materialized sink
    * - prints out all the strings which are lowercase
    * - count the string that are short (< 5 chars)
    */

  val wordSource: Source[String, NotUsed] = Source(List("Akka", "is", "awesome", "rock", "the", "scala"))
  val printer: Sink[String, Future[Done]] = Sink.foreach[String](println)
  val counter: Sink[String, Future[Int]] = Sink.fold[Int, String](0)((count, _) => count + 1)

  val complexWorkSink1: Sink[String, NotUsed] = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowerCaseFlow = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringFlow = builder.add(Flow[String].filter(_.length < 5))
      broadcast.out(0) ~> lowerCaseFlow ~> printer
      broadcast.out(1) ~> shortStringFlow ~> counter
      SinkShape(broadcast.in)
    }
  )

  /**
    * Output NotUsed out of this Sink, I want count , duh
    */
  // wordSource.runWith(complexWorkSink1)

  val complexWorkSink2: Sink[String, Future[Int]] = Sink.fromGraph(

    GraphDSL.create(counter) { implicit builder =>
      counterShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[String](2))
        val lowerCaseFlow = builder.add(Flow[String].filter(word => word == word.toLowerCase))
        val shortStringFlow = builder.add(Flow[String].filter(_.length < 5))
        broadcast.out(0) ~> lowerCaseFlow ~> printer
        broadcast.out(1) ~> shortStringFlow ~> counterShape
        SinkShape(broadcast.in)
    }
  )

  val res: Future[Int] = wordSource.toMat(complexWorkSink2)(Keep.right).run()
  //val res: Future[Int] = wordSource.runWith(complexWorkSink2)

  import system.dispatcher

  res.map { x =>
    println(s"count from sink: ${x}")
  }

  Thread.sleep(2000)
  println(":" * 30)

  val complexWorkSink3: Sink[String, Future[Int]] = Sink.fromGraph(

    GraphDSL.create(counter, printer)((counterMatVal, _) => counterMatVal) { implicit builder =>
      (counterShape, printerShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[String](2))
        val lowerCaseFlow = builder.add(Flow[String].filter(word => word == word.toLowerCase))
        val shortStringFlow = builder.add(Flow[String].filter(_.length < 5))
        broadcast.out(0) ~> lowerCaseFlow ~> counterShape
        broadcast.out(1) ~> shortStringFlow ~> printerShape
        SinkShape(broadcast.in)
    }
  )

  val res2: Future[Int] = wordSource.toMat(complexWorkSink3)(Keep.right).run()

  res2.map { x =>
    println(s"count from sink: ${x}")
  }

  Thread.sleep(2000)
  println(":" * 30)

}

object GraphMaterializedValues_2 extends App {

  implicit val system = ActorSystem("GraphMaterializedValues-1")
  implicit val materializer = ActorMaterializer()

  //materialized flow

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink: Sink[B, Future[Int]] = Sink.fold[Int, B](0)((count, _) => count + 1)
    Flow.fromGraph(
      GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[B](2))
        val originalFlow = builder.add(flow)
        originalFlow.out ~> broadcast
        broadcast.out(0) ~> counterSinkShape
        FlowShape(originalFlow.in, broadcast.out(1))
      }
    )

  }

  val source: Source[Int, NotUsed] = Source(1 to 20)
  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map[Int](x => x)
  val sink: Sink[Any, Future[Done]] = Sink.ignore

  val result: Future[Int] = source.viaMat(enhanceFlow(flow))(Keep.right).toMat(sink)(Keep.left).run()


 import system.dispatcher
  result.map { x =>
    println(s"count from sink: ${x}")
  }

  Thread.sleep(2000)

}