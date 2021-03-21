package learning.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ActorMaterializer, ClosedShape, FanInShape, Graph, OverflowStrategy, UniformFanInShape}

object GraphCycles_1 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val accelerator: Graph[ClosedShape.type, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementer = builder.add(Flow[Int].map { x =>
      println(s"Accelerating: ${x}")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementer
                   mergeShape <~ incrementer
    ClosedShape
  }

 // RunnableGraph.fromGraph(accelerator).run - graph cycle deadlock!

  //Solution 1 - MergePreferred

  val actualAccelerator: Graph[ClosedShape.type, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementer = builder.add(Flow[Int].map { x =>
      println(s"Accelerating: ${x}")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementer
    mergeShape.preferred <~ incrementer
    ClosedShape
  }

   //RunnableGraph.fromGraph(actualAccelerator).run

  //Solution 2 - Buffers
  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~>  mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  RunnableGraph.fromGraph(bufferedRepeater).run

  /*
  cycles risk deadlocking
  - add bounds [bounded buffers] to the number of elements in the cycle

  boundedness vs liveness
 */
}

object FiboUsingCycle_1 extends App {
  
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val fibonacciGenerator: Graph[UniformFanInShape[BigInt, BigInt], NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val zip = builder.add(Zip[BigInt, BigInt])
    val merge = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val flow = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      Thread.sleep(1000)
      (pair._2, pair._1 + pair._2)

    })
    val fiboFlow = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      pair._1
    })
     zip.out ~> merge ~> flow ~> broadcast ~> fiboFlow
    
                merge.preferred <~ broadcast
                
    UniformFanInShape(fiboFlow.out, zip.in0, zip.in1)
  }

    val fiboGraph = RunnableGraph.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val source1 = builder.add(Source.single[BigInt](1))
        val source2 = builder.add(Source.single[BigInt](1))

        val sink = builder.add(Sink.foreach[BigInt](println))
        val fibo = builder.add(fibonacciGenerator)

        source1 ~> fibo.in(0)
        source2 ~> fibo.in(1)
        fibo.out ~> sink
        ClosedShape
      }
    )
  fiboGraph.run()
  
}

object SimplifiedFibo extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val fiboGraph: Graph[ClosedShape.type, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val s1, s2 = Source.single(BigInt(1))
    val zip = builder.add(Zip[BigInt, BigInt])
    val merge = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val flow = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      println(pair._1)
      Thread.sleep(1000)
      (pair._2, pair._1 + pair._2)

    })

    s1 ~> zip.in0
    s2 ~> zip.in1
    zip.out ~> merge ~> flow ~> merge

    ClosedShape
  }
  RunnableGraph.fromGraph(fiboGraph).run()

}