package learning.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object First_Principles_1 extends App {

  implicit val system = ActorSystem("FirstPrinciples-1")
  implicit val materializer = ActorMaterializer()

  //sources
  val source: Source[Int, NotUsed] = Source(1 to 10)
  //sink
  val sink: Sink[Int, Future[Done]] = Sink.foreach(println)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
  val res: NotUsed = graph.run

}

object First_Principles_2 extends App {

  implicit val system = ActorSystem("FirstPrinciples-2")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
  val res: NotUsed = graph.run

}

object First_Principles_3 extends App {

  implicit val system = ActorSystem("FirstPrinciples-2")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)

  val graph: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)
  val res: Future[Int] = graph.run

  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import java.util.concurrent.TimeUnit

  println(Await.result(res, Duration(1, TimeUnit.SECONDS)))
}

object First_Principles_4 extends App {

  implicit val system = ActorSystem("FirstPrinciples-3")
  implicit val materializer = ActorMaterializer()

  //flow transforms elements

  val source: Source[Int, NotUsed] = Source[Int](1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 2) //mentioning type is necessary

  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  val flowToSink: Sink[Int, NotUsed] = flow.to(sink) //toMat(sink)(Keep.left) //keeping materialized value of left i.e flow i.e Not Used

  val typ1: RunnableGraph[NotUsed] = sourceWithFlow.to(sink)
  val typ2: RunnableGraph[NotUsed] = source.to(flowToSink)

  val rG: RunnableGraph[NotUsed] = source.via(flow).to(sink)
  val result: NotUsed = rG.run()
}

object First_Principles_5 extends App {

  implicit val system = ActorSystem("FirstPrinciples-5")
  implicit val materializer = ActorMaterializer()
 /** Exception in thread "main" java.lang.NullPointerException: Element must not be null, rule 2.13*/
  //null values are not allowed, use Option instead
  val illegalSource = Source.single[String](null)
  illegalSource.to(Sink.foreach(println)).run()
}

object First_Principles_6 extends App {

  implicit val system = ActorSystem("FirstPrinciples-5")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //various kinds of sources

  val finiteSource: Source[Int, NotUsed] = Source.single(1)
  val anotherFiniteSource: Source[Int, NotUsed] = Source(List(1,2,3,4))
  val emptySource: Source[Int, NotUsed] = Source.empty[Int]

  Run_App.execute(finiteSource.to(sink))

  Thread.sleep(1000)
  println("---1----")

  Run_App.execute(anotherFiniteSource.to(sink))

  Thread.sleep(1000)
  println("---2----")

  Run_App.execute(emptySource.to(sink))
  println("--3----")
  val infiniteStream: Source[Int, NotUsed] = Source(Stream.from(1)) // do not confuse an akka stream with a "collection" Stream
  //Run_App.execute(infiniteStream.to(sink))

  Thread.sleep(1000)
  println("--4----")

  import system.dispatcher
  val futureSource: Source[Int, NotUsed] = Source.fromFuture(Future(42))
  val res: RunnableGraph[NotUsed] = futureSource.to(sink)
  Run_App.execute(res)

  Thread.sleep(1000)
  println("--5----")

}

object First_Principles_7 extends App {

  implicit val system = ActorSystem("FirstPrinciples-5")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)

  val theMostBoringSink: Sink[Any, Future[Done]] = Sink.ignore

  Run_App.execute(source.to(theMostBoringSink))

  Thread.sleep(1000)
  println("----1----")

  val forEachSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  Run_App.execute(source.to(forEachSink))

  Thread.sleep(1000)
  println("----2----")

  val headSink: Sink[Int, Future[Int]] = Sink.head[Int] //retrieves head and then closes the stream
  Run_App.executeWithPrintln(source.toMat(headSink)(Keep.right))

  Thread.sleep(1000)
  println("----3----")

  val foldSink = Sink.fold[Int, Int](0)(_ + _)
  Run_App.executeWithPrintln(source.toMat(foldSink)(Keep.right))

  Thread.sleep(1000)
  println("----4----")


}

object First_Principles_8 extends App {

    implicit val system = ActorSystem("FirstPrinciples-5")
    implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //Flows, usually mapped to collection operators
  val mapFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 2)
  val takeFlow: Flow[Int, Int, NotUsed] = Flow[Int].take(2)
  //same way drop, filer, NOT have flatMap

  Run_App.execute(source.via(mapFlow).to(sink))

  Thread.sleep(1000)
  println("----1----")

  Run_App.execute(source.via(takeFlow).to(sink))

  Thread.sleep(1000)
  println("----2----")

  Run_App.execute(source.via(mapFlow).via(takeFlow).to(sink))
}

object First_Principles_9 extends App {

  implicit val system = ActorSystem("FirstPrinciples-5")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //some syntactic sugar

  val mapSource: Source[Int, NotUsed] = Source(1 to 4).map(_ + 2) // equivalent to Source(1 to 10).via(Flow[Int].map(_ + 2))

  Run_App.execute(mapSource.to(sink))

  Thread.sleep(1000)
  println("----1----")

  //run Stream directly
  val result: Future[Done] = mapSource.runWith(sink) //equivalent to mapSource.to(Sink.foreach(println)).run()

  Thread.sleep(1000)
  println("----2----")

  val result2: Future[Done] = mapSource.runForeach(println) // equivalent to mapSource.to(Sink.foreach[Int](println))

  Thread.sleep(1000)
  println("----2----")

  // OPERATORS = components

  val names = List("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams")
  val nameSource = Source(names)
  val longNameFlow = Flow[String].filter(name => name.length > 5)
  val limitFlow = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)

  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()

  Thread.sleep(1000)
  println("----3----")

  //same as above
  nameSource.filter(_.length > 5).take(2).runForeach(println)

}


object Run_App extends App {
  def execute[T](rG: RunnableGraph[T])(implicit actorMaterializer: ActorMaterializer) = {
    rG.run()
  }

  def executeWithPrintln[T](rG: RunnableGraph[T])(implicit actorMaterializer: ActorMaterializer) = {
    println{
      rG.run()
    }
  }
}