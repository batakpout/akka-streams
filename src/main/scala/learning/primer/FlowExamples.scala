package learning.primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

object FlowExamples_1 extends App {

  class SomeConnection {

    val flow1: Flow[Int, Int, NotUsed] = Flow[Int].map { x=>
      println(s"from flow1: x is: $x")
      x + 1
    }

    def comeJoin(f: Flow[Int, Int, NotUsed]):RunnableGraph[NotUsed]  = flow1.join(f)
  }

  implicit val system = ActorSystem("fe-1")
  implicit val materializer = ActorMaterializer()

  val s: Source[SomeConnection, NotUsed] = Source.single(new SomeConnection)

  val flow2: Flow[Int, Int, NotUsed] = Flow[Int].map { x =>
    println(s"from flow2: x is: $x")
    x * 2
  }

  println("running")
  val si = Sink.foreach[SomeConnection] { c =>
    c.comeJoin(flow2).run()
  }
  s.runWith(si)
}