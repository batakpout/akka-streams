package learning.advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream._

import scala.collection.immutable
import scala.concurrent.duration.DurationInt

/**
  * Create Components with arbitrary inputs and outputs.
  * e.g An M * N Balance component
  */

object CustomGraphShapes_1 extends App {

  implicit val system = ActorSystem("CGS")
  implicit val materializer = ActorMaterializer()

  case class Balance2x3(
                         in0: Inlet[Int],
                         in1: Inlet[Int],
                         out0: Outlet[Int],
                         out1: Outlet[Int],
                         out2: Outlet[Int]
                       ) extends Shape {

    //inlets, outlets method need to return a well defined order of elements in List
    override def inlets: immutable.Seq[Inlet[_]] = List(in0, in1)

    override def outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balanced2x3Impl: Graph[Balance2x3, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance // merge.out = balance.in

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2),
    )
  }

  val balance2x3Graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(Stream.from(1)).throttle(1, 1.second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1.second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[sink $index] Received $element, current count is $count")
        count + 1
      })

      val sink1: SinkShape[Int] = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3: Balance2x3 = builder.add(balanced2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1
      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3
      ClosedShape

    }
  )

  balance2x3Graph.run()

}

object CustomGraphShapes_2 extends App {

  implicit val system = ActorSystem("CGS")
  implicit val materializer = ActorMaterializer()

  case class BalanceMxN[T](inlets: List[Inlet[T]], outlets: List[Outlet[T]]) extends Shape {
    override def deepCopy(): Shape = BalanceMxN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMxN {

    def apply[T](input: Int, output: Int): Graph[BalanceMxN[T], NotUsed] = {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val merge = builder.add(Merge[T](input))
        val balance = builder.add(Balance[T](output))
        merge ~> balance
        BalanceMxN(merge.inlets.toList, balance.outlets.toList)
      }
    }
  }

  val balanceMxNGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[sink $index] Received $element, current count is $count")
        count + 1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3 = builder.add(BalanceMxN[Int](2, 3))
      slowSource ~> balance2x3.inlets(0)
      fastSource ~> balance2x3.inlets(1)

      balance2x3.outlets(0) ~> sink1
      balance2x3.outlets(1) ~> sink2
      balance2x3.outlets(2) ~> sink3
      ClosedShape

    })

  //balanceMxNGraph.run()

  val balanceMxNGraphString = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = Source.repeat("Stream-1").throttle(1, 1 second)
      val source2 = Source.repeat("Stream-2").throttle(2, 1 second)
      val source3 = Source.repeat("Stream-3").throttle(3, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: String) => {
        println(s"[sink $index] Received $element, current count is $count")
        count + 1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))
      val sink4 = builder.add(createSink(4))

      val balance2x3: BalanceMxN[String] = builder.add(BalanceMxN[String](3, 4))
      source1 ~> balance2x3.inlets(0)
      source2 ~> balance2x3.inlets(1)
      source3 ~> balance2x3.inlets(2)

      balance2x3.outlets(0) ~> sink1
      balance2x3.outlets(1) ~> sink2
      balance2x3.outlets(2) ~> sink3
      balance2x3.outlets(3) ~> sink4

      ClosedShape

    })

  balanceMxNGraphString.run()
}