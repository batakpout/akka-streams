package learning.graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanInShape2, FanOutShape2, FlowShape, Graph, SinkShape, SourceShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source, Tcp, Zip, ZipWith}
import akka.util.ByteString

import java.util.Date
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

      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

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

  val d: Graph[FlowShape[Int, Int], NotUsed] = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val incrementerShape: FlowShape[Int, Int] = builder.add(incrementer)
      val multiplierShape: FlowShape[Int, Int] = builder.add(multiplier)

      incrementerShape ~> multiplierShape
      FlowShape(incrementerShape.in, multiplierShape.out)
    }


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
      GraphDSL.create() { implicit builder =>
        val sourceShape: SourceShape[B] = builder.add(source)
        val sinkShape: SinkShape[A] = builder.add(sink)

        /**
          * Input of this flow-shape should be input for the sink, so sink goes first
          */
        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }


  // You'd want to use this kind of graph when you want to "connect" two completely distinct linear graphs and chain them into one.
  //https://doc.akka.io/docs/akka/current/stream/operators/Flow/fromSinkAndSource.html
  val f: Flow[Int, Int, NotUsed] = Flow.fromSinkAndSourceCoupled(Sink.foreach[Int](println), Source(1 to 10))


}

object FlowFromSinkAndShape1 extends App {

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

object FlowFromSinkAndShape2 extends App {

  implicit val system = ActorSystem("Intro2")
  implicit val materializer = ActorMaterializer()

  // close in immediately
  val source = Source.tick(1.second, 1.second, "tick")
  val flow: Flow[Any, String, NotUsed] = Flow.fromSinkAndSourceCoupled(Sink.foreach(println), source)
  val r = source.via(flow).to(Sink.foreach(println)).run()

}

object OpenGraphsMore1 extends App {

  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  /**
    * Build a Max3 operator
    * -3 inputs of type int
    * -the maximum of the 3
    */

  val max3StaticGraph: Graph[UniformFanInShape[Int, Int], NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val max1: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    max1.out ~> max2.in0
    //uniform because inlets of same type, same goes for UniformFanOutShape
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source(1 to 10).map(_ => 5)
  val source3 = Source((1 to 10).reverse)
  val maxSink = Sink.foreach[Int](println)

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max3Shape: UniformFanInShape[Int, Int] = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape ~> maxSink
      ClosedShape
    }
  )

  max3RunnableGraph.run()

}

object OpenGraphsMore2 extends App {

  //same as OpenGraphsMore1
  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()


  val source1 = Source(1 to 10)
  val source2 = Source(1 to 10).map(_ => 5)
  val source3 = Source((1 to 10).reverse)
  val maxSink = Sink.foreach[Int](println)

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max1: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
      val max2: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

      max1.out ~> max2.in0
      val shape: UniformFanInShape[Int, Int] = UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)

      source1 ~> shape.in(0)
      source2 ~> shape.in(1)
      source3 ~> shape.in(2)

      shape.out ~> maxSink
      ClosedShape
    }
  )

  max3RunnableGraph.run()

}

// same for UniformFanOutShape

/*
  Non-uniform fan out shape

  Processing bank transactions
  Txn suspicious if amount > 10000

  Streams component for txns
  - output1: let the transaction go through
  - output2: suspicious txn ids
 */

object BankTransactionCatch extends App {

  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("5273890572", "Paul", "Jim", 100, new Date),
    Transaction("3578902532", "Daniel", "Jim", 100000, new Date),
    Transaction("5489036033", "Jim", "Alice", 7000, new Date)
  ))

  val bankProcessorSink: Sink[Transaction, Future[Done]] = Sink.foreach[Transaction](println)
  val suspiciousAnalysisServiceSink: Sink[String, Future[Done]] = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val broadcast: UniformFanOutShape[Transaction, Transaction] = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter: FlowShape[Transaction, Transaction] = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor: FlowShape[Transaction, String] = builder.add(Flow[Transaction].map[String](txn => txn.id))

    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val shape: FanOutShape2[Transaction, Transaction, String] = builder.add(suspiciousTxnStaticGraph)
      transactionSource ~> shape.in
      shape.out0 ~> bankProcessorSink
      shape.out1 ~> suspiciousAnalysisServiceSink

      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()

}