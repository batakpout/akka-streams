package from_daniel.part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object MaterializingStreams1 extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  //normally when using via, to, left most materialized value is kept, so here of Source
  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  // val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a, b) => a + b)
  val sumFuture = source.runWith(sink)
  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all elements is :$value")
    case Failure(ex) => println(s"The sum of the elements could not be computed: $ex")
  }
}

object MaterializingStreams2 extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // choosing materialized values
  private val source = Source(1 to 10)
  val flow = Flow[Int].map(x => x + 1)
  val sink = Sink.foreach[Int](println)
  val result = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.right)
  result.run().onComplete {
    case Success(_) => println("Stream processing finished")
    case Failure(exception) => println(s"Stream processing failed with : $exception")
  }
}

object MaterializingStreams3 extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // choosing materialized values
  val sentences = List("The quick brown fox jumps over the lazy dog")
  private val source = Source[String](sentences)

  val splitFlow: Flow[String, Int, NotUsed] = Flow[String].map[Int](_.split(" ").length)
  val reduceFlow: Flow[Int, Int, NotUsed] = Flow[Int].reduce[Int](_ + _)
  val printSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  val reduceSink: Sink[Int, Future[Int]] = Sink.reduce[Int](_ + _)

  val graphX = source
    .viaMat(splitFlow)(Keep.right)
    .viaMat(reduceFlow)(Keep.right)
    .toMat(reduceSink)(Keep.right)
    .run()

  val source1 = Source(1 to 10)
  val sink1 = Sink.reduce[Int]((a, b) => a + b)
  val graph1 = source1.toMat(sink1)(Keep.right)

  val sourceTest = Source(1 to 100)
  val flow = Flow[Int].map[Double](x => x + 2)
  val sink = Sink.fold[String, Double]("")(_ + _)

  val sinkB: RunnableGraph[NotUsed] = sourceTest.via(flow).to(sink)
  val sinkC: RunnableGraph[NotUsed] = sourceTest.viaMat(flow)(Keep.left).toMat(sink)(Keep.left)
  val sinkD: RunnableGraph[Future[String]] = sourceTest.viaMat(flow)(Keep.right).toMat(sink)(Keep.right)
  val sinkE: RunnableGraph[((NotUsed, NotUsed), Future[String])] =
    sourceTest.viaMat(flow)(Keep.both).toMat(sink)(Keep.both)

  val sinkF: Future[String] = sourceTest.viaMat(flow)(Keep.right).runWith(sink)

}

object MaterializingStreams4 extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val source = Source(1 to 2)
  val flow = Flow[Int].map[Int](x => x + 2)
  val sinkA = Sink.fold[String, Int]("")(_ + _)
  val graphNotUsed: RunnableGraph[Future[String]] = source.viaMat(flow)(Keep.right).toMat(sinkA)(Keep.right)

  graphNotUsed.run().onComplete {
    case Success(e) => println(e)
    case Failure(e) => println(e.toString)
  }


}

object MaterializingStreams5 extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  val graph1: RunnableGraph[Future[Done]] = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  val graph: RunnableGraph[NotUsed] = simpleSource.viaMat(simpleFlow)(Keep.left).toMat(simpleSink)(Keep.left)
  graph.run()
}

object MaterializingStreams6 extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // sugars
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // source.to(Sink.reduce)(Keep.right)
  val res = Source(1 to 10).runReduce[Int](_ + _) // same

  // backwards , ordering doesn't matter.
  Sink.foreach[Int](println).runWith(Source.single(42)) // source(..).to(sink...).run()
  // both ways
  val result = Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)
}

object MaterializingStreams7 extends App {
  /**
    * - return the last element out of a source (use Sink.last)
    * - compute the total word count out of a stream of sentences
    *   - map, fold, reduce
    */

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val f1 = Source(1 to 10).toMat(Sink.last)(Keep.right).run()
  f1.onComplete {
    case Success(e) => println(e)
    case Failure(e) => println(e.toString)
  }
  val f2 = Source(1 to 10).runWith(Sink.last)
  f2.onComplete {
    case Success(e) => println(e)
    case Failure(e) => println(e.toString)
  }
}


object MaterializingStreams8 extends App {


  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()


  val sentenceSource = Source(List(
    "Akka is awesome",
    "I love streams",
    "Materialized values are killing me"
  ))
  val wordCountSink = Sink.fold[Int, String](0)((currentWordsCount, newSentence) => currentWordsCount + newSentence.split(" ").length)
  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  println( Await.result(g1, 1.seconds) )
  val g2 = sentenceSource.runWith(wordCountSink)
  println( Await.result(g2, 1.seconds) )
  val g3 = sentenceSource.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  println( Await.result(g3, 1.seconds) )
}

object MaterializingStreams9 extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()
  val sentenceSource = Source(List(
    "Akka is awesome",
    "I love streams",
    "Materialized values are killing me"
  ))
  val wordCountFlow = Flow[String].fold[Int](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g4 = sentenceSource.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()
  println(Await.result(g4, 1.seconds) )
  val g5 = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  println( Await.result(g5, 1.seconds) )
  val g6 = sentenceSource.via(wordCountFlow).runWith(Sink.head)
  println( Await.result(g5, 1.seconds) )
  val g7 = wordCountFlow.runWith(sentenceSource, Sink.head)._2
  println( Await.result(g7, 1.seconds) )
}
