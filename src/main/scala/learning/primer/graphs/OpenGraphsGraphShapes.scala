package learning.primer.graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source, Tcp, Zip}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object OpenGraphsGraphShapes_1 extends App {

  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  /**
    * A Composite source that concatenates 2 sources
    * - emits ALL the elements from the first source and
    * - then ALL the elements from the second
    */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph: Source[Int, NotUsed] = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      /**
        * A concat takes all elements from first input and pushes them out and so on and so forth
        */
      val concat = builder.add(Concat[Int](2))
      firstSource ~> concat
      secondSource ~> concat
      SourceShape(concat.out)
    } // return source shape now
  )
  sourceGraph.runWith(Sink.foreach(println))
}

object OpenGraphsGraphShapes_2 extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  /**
    * A Complex sink:
    * we have a single source to complex sink
    */

  val firstSource = Source(1 to 10)

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))


  val sourceGraph: Sink[Int, NotUsed] = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  firstSource.runWith(sourceGraph)

}

object OpenGraphsGraphShapes_3 extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  /**
    * A Complex Flow:
    */

  val source = Source(1 to 10)
  val sink = Sink.foreach(println)

  val flow1 = Flow[Int].map[Int](_ + 1)
  val flow2 = Flow[Int].map[Int](_ * 10)

  val flowGraph: Flow[Int, (Int, Int), NotUsed] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int])

      broadcast ~> flow1 ~> zip.in0
      broadcast ~> flow2 ~> zip.in1


      FlowShape(broadcast.in, zip.out)
    }
  )

 source.via(flowGraph).runWith(sink)

}

object OpenGraphsGraphShapes_4 extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  /**
    * A Complex Flow:
    */

  val source = Source(1 to 10)
  val sink = Sink.foreach(println)

  val incrementer: Flow[Int, Int, NotUsed] = Flow[Int].map[Int](_ + 1)
  val multiplier: Flow[Int, Int, NotUsed] = Flow[Int].map[Int](_ * 10)

  val flowGraph: Flow[Int, Int, NotUsed] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val incrementerShape: FlowShape[Int, Int] = builder.add(incrementer)
      val multiplierShape: FlowShape[Int, Int] = builder.add(multiplier)

      incrementerShape ~> multiplierShape
      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  )

  source.via(flowGraph).runWith(sink)

}

object OpenGraphsGraphShapes_5 extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  /**
    * A Complex Flow:
    */

  val source = Source(1 to 10)
  val sink = Sink.foreach(println)

  val incrementer = Flow[Int].map[Int](_ + 1)
  val multiplier = Flow[Int].map[Int](_ * 10)

  def fromSinkAndSource[A, B](source: Source[B, _], sink: Sink[A, _]): Flow[A, B, NotUsed] = {
    Flow.fromGraph(
      GraphDSL.create(){implicit builder =>
        val sourceShape: SourceShape[B] = builder.add(source)
        val sinkShape: SinkShape[A] = builder.add(sink)

        /**
          * Input of this flow-shape should be input for the sink, so sink goes first
          */
        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }


  // in streams API
  val f: Flow[Int, Int, NotUsed] = Flow.fromSinkAndSourceCoupled(Sink.foreach[Int](println), Source(1 to 10))




}
object jj extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  // close in immediately
  val sink = Sink.cancelled[ByteString]
  // periodic tick out
  val source =
    Source.tick(1.second, 1.second, "tick").map(_ => ByteString("Hello Telnet" + "\n"))

  val serverFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(sink, source)

  val r: Future[Done] = Tcp().bind("127.0.0.1", 9999).runForeach { incomingConnection: Tcp.IncomingConnection =>
    incomingConnection.handleWith(serverFlow)
  }
}