package learning.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, Graph, SinkShape, SourceShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object SimpleExamples_1 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val s1 = Source(1 to 3)


  //val sink1 = Sink.fold[Int, Int](0)((seed, tup) => seed + tup)
  val sink1: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((seed, item) => seed + item)

  /*  val futureResult1 = RunnableGraph.fromGraph(
      GraphDSL.create(sink1) { implicit builder: GraphDSL.Builder[Future[Int]] =>
        sinkMat =>

          import GraphDSL.Implicits._

          val f1 = Flow[Int].map(_ + 1)
          val f2 = Flow[Int].map(_ * 2)

          val broadcast = builder.add(Broadcast[Int](2))
          val zip = builder.add(Zip[Int, Int])

          s1 ~> broadcast ~> f1 ~> zip.in0
          broadcast ~> f2 ~> zip.in1
          zip.out ~> sinkMat

          ClosedShape
      }

    ).run()*/

  val futureResult2 = RunnableGraph.fromGraph(
    GraphDSL.create(sink1) { implicit builder: GraphDSL.Builder[Future[Int]] =>
      sinkMat =>

        import GraphDSL.Implicits._

        val f1 = Flow[Int].map(_ + 1)
        val f2 = Flow[Int].map(_ * 2)

        val broadcast = builder.add(Broadcast[Int](2))
        val merge = builder.add(Merge[Int](2))

        s1 ~> broadcast ~> f1 ~> merge ~> sinkMat
        broadcast ~> f2 ~> merge

        ClosedShape
    }

  ).run()

  val result: Int = Await.result(futureResult2, 2.seconds)
  println(s"Sum is: $result")
}

object SimpleExamples_2 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()


  //val sink1 = Sink.fold[Int, Int](0)((seed, tup) => seed + tup)
  val sink1: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((seed, item) => seed + item)


  val futureResult2: Graph[SourceShape[Int], NotUsed] = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._
    val s1, s2 = Source(1 to 5)
    val merge = builder.add(Merge[Int](2))

    s1 ~> merge
    s2 ~> merge

    SourceShape(merge.out)
  }

  val source: Source[Int, NotUsed] = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val s1, s2 = Source(1 to 5)
      val merge = builder.add(Merge[Int](2))

      s1 ~> merge
      s2 ~> merge

      SourceShape(merge.out)
    }
  )

  source.runWith(Sink.foreach[Int](println))
}

object SimpleExamples_3 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()


  //val sink1 = Sink.fold[Int, Int](0)((seed, tup) => seed + tup)
  val sink1: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((seed, item) => seed + item)


  val futureResult2: Graph[SourceShape[Int], NotUsed] = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._
    val s1, s2 = Source(1 to 5)
    val merge = builder.add(Merge[Int](2))

    s1 ~> merge
    s2 ~> merge

    SourceShape(merge.out)
  }

  val fanInGraph: Graph[UniformFanInShape[Int, Int], NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val merge = builder.add(Merge[Int](2))
    UniformFanInShape(merge.out, merge.in(0), merge.in(1))
  }

  val f = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val s1, s2 = Source(1 to 5)
      val fanInShape: UniformFanInShape[Int, Int] = builder.add(fanInGraph)

      s1 ~> fanInShape.in(0)
      s2 ~> fanInShape.in(1)

      SourceShape(fanInShape.out) // SinkShape(broadcast.in)

    }
  )


  //source.runWith(Sink.foreach[Int](println))
}

object GraphApi extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val s1, s2 = Source(1 to 5)
  val sink1, sink2 = Sink.foreach(println)

  val mergedSource: Source[Int, NotUsed] = {
    Source.combine(s1, s2)(Merge[Int](_))
  }

 // mergedSource.runWith(Sink.foreach[Int](println))

  val x: Broadcast[Int] = Broadcast[Int](2)
  val y: Int => Broadcast[Int] = Broadcast[Int](_)
  val z: Broadcast[Int] = y(2)

  val splitSink: Sink[Int, NotUsed] = {
    Sink.combine(sink1, sink2)(Broadcast[Int](_))
  }
  mergedSource.runWith(splitSink)
}



object UpgradeTest_With_Broadcast extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val s1 = Source(1 to 5)


  val sink1: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((seed, item) => seed + item)


  val futureResult2: Flow[Int, Int, NotUsed] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>

        import GraphDSL.Implicits._

      val f1 = Flow[Int].map {x =>
        println(s"Inside Flow1: ${x}")
        x + 1
      }
      val f2 = Flow[Int].map{ x =>
        println(s"Inside Flow2: ${x}")
        x * 2
      }

        val broadcast = builder.add(Broadcast[Int](3))
        val merge = builder.add(Merge[Int](3))

        broadcast ~> f1 ~> merge
        broadcast ~> f2 ~> merge
        broadcast ~> merge

        FlowShape(broadcast.in, merge.out)
    }

  )

  s1.via(futureResult2).to(Sink.ignore).run()

}

object UpgradeTest_With_Balance extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val s1 = Source(1 to 6)


  val sink1: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((seed, item) => seed + item)


  val futureResult2: Flow[Int, Int, NotUsed] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._

      val f1 = Flow[Int].map {x =>
         println(s"Inside Flow1: ${x}")
        x + 1
      }
      val f2 = Flow[Int].map{ x =>
        println(s"Inside Flow2: ${x}")
        x * 2
      }

      val balance = builder.add(Balance[Int](3))
      val merge = builder.add(Merge[Int](3))

      balance ~> f1 ~> merge
      balance ~> f2 ~> merge
      balance ~> merge

      FlowShape(balance.in, merge.out)
    }

  )

  s1.via(futureResult2).to(Sink.ignore).run()

}