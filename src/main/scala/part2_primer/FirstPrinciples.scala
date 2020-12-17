package part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples1 extends App {

  implicit val system = ActorSystem("FirstPrinciples-1")
  implicit val materializer = ActorMaterializer()
  // sources
  val source: Source[Int, NotUsed] = Source(1 to 10)
  // sinks
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
   graph.run()
}

object FirstPrinciples2 extends App {

  implicit val system = ActorSystem("FirstPrinciples")
  implicit val materializer = ActorMaterializer()

  // flows transform elements

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)

  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)

  val flowToSink: Sink[Int, NotUsed] = flow.to(sink)

   //sourceWithFlow.to(sink).run()
  // source.to(flowToSink).run()
   source.via(flow).to(sink).run()
}

object FirstPrinciples3 extends App {
  implicit val system = ActorSystem("FirstPrinciples")
  implicit val materializer = ActorMaterializer()

  // nulls are NOT allowed
   val illegalSource = Source.single[String](null)
   illegalSource.to(Sink.foreach(println)).run()
  // use Options instead
}

object FirstPrinciples4 extends App {

  implicit val system = ActorSystem("FirstPrinciples")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource: Source[Int, NotUsed] = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1)) // do not confuse an Akka stream with a "collection" Stream

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.fromFuture(Future(42))

  // sinks
  val theMostBoringSink: Sink[Any, Future[Done]] = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT have flatMap

  // source -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
    doubleFlowGraph.run()

}
object FirstPrinciples5 extends App {

  implicit val system = ActorSystem("FirstPrinciples")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  // syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2) // Source(1 to 10).via(Flow[Int].map(x => x * 2))
  // run streams directly
    //mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /**
    * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names with length > 5 characters.
    *
    */
  val names = List("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams")
  val nameSource = Source(names)
  val longNameFlow = Flow[String].filter(name => name.length > 5)
  val limitFlow = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)

  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()
 // nameSource.filter(_.length > 5).take(2).runForeach(println)

}
