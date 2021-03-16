package learning.advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, SubFlow}
import learning.advanced.SubStreams_1.system

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Create streams dynamically
  * Process sub-streams uniformly
  *
  * group by:  groupBy, all operations are applied to all sub-streams, is then groupBy used only for parallelism/performance
    Yes, for performance and for independent processing per group, because without groupBy,
    it would be really hard to do automatic grouping on a stream.
  */

object SubStreams_1 extends App {

  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()

  //1- grouping a stream by a certain function
  val wordsSource = Source(List("Akka", "is", "amazing", "learning", "substreams", "love", "like", ""))
  val groups: SubFlow[String, NotUsed, wordsSource.Repr, RunnableGraph[NotUsed]] = {
    wordsSource.groupBy(30, word => if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))
  }

  // this sink runs for each sub-stream, i.e separate fold for each sub-stream, every sub-stream will have different
  //materialization of sink component
  val sink: Sink[String, Future[Int]] = Sink.fold[Int, String](0)((count, word) => {
    println("Count===> " + count)
    val newCount = count + 1
    println(s"I just received $word, count is: $newCount")
    newCount
  })
  val g: RunnableGraph[NotUsed] = groups.to(sink)
  // g.run()

  //2- merge sub-stream back into stream
  val textSource = Source(List(
    "I love Akka Streams",
    "this is amazing",
    "learning from Rock the JVM"
  ))

  val totalCharCountFuture = textSource.groupBy(2, string => string.length % 2)
    .map(_.length) // our expensive computation here, applies for each sub-stream
    .mergeSubstreamsWithParallelism(2) // check documentation for details
    //.mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  import system.dispatcher

  totalCharCountFuture.onComplete {
    case Success(s) => println(s"Total char count: $s")
    case Failure(e) => println(s"Char Computation failed: $e")
  }


}
object SubStreams_2 extends App {

  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()

  // Splitting a stream into sub-streams when a condition is met
  val text = "I love Akka Streams\n" +
    "this is amazing\n" +
    "learning from Rock the JVM\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
  /**
     As the data flows through stream, when the incomming character is \n, at that point a new sub-stream is formed
     till another \n is encountered, so here 3 sub-streams created
    */
    .filterNot(_ == '\n')
    .map(_ => 1) // return 1 for each character
    .mergeSubstreams //at his point I have single stream of numbers
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  import system.dispatcher
  anotherCharCountFuture.onComplete {
    case Success(s) => println(s"Total char count: $s")
    case Failure(e) => println(s"Char Computation failed: $e")
  }





}

object FlatMapping extends App {

  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()


  val simpleSource = Source(List(1, 2))
  val s1: Source[Int, NotUsed] = simpleSource.flatMapConcat(x => Source(x to (3 * x))) //internally sub-streams are created
    s1.runWith(Sink.foreach(println))
  println("----")
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))

  //mergeSubstreamsWithParallelism(2) === breadth = 2

}


object PlayTime_1 extends App {

  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()

  val textSource = Source(List(
    "I love Akka Streams",
    "this is amazing",
    "learning from Rock the JVM"
  ))

  val x1: SubFlow[String, NotUsed, textSource.Repr, RunnableGraph[NotUsed]] = textSource.groupBy(2, string => string.length % 2)
  val y1: SubFlow[Int, NotUsed, textSource.Repr, RunnableGraph[NotUsed]] = x1.map(x=> x.length) // our expensive computation here, applies for each sub-stream
  val z1: Source[Int, NotUsed] = y1.mergeSubstreamsWithParallelism(2)

  val q1: RunnableGraph[Future[Int]] = z1.toMat(Sink.reduce[Int](_ + _))(Keep.right)
  val r: Future[Int] =  q1.run()

}

object ParallelismTest_1 extends App {
//https://stackoverflow.com/questions/65251318/does-the-groupby-in-akka-stream-creates-substreams-that-run-in-parallel/65253987#65253987
  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()

  val textSource = Source(List(
    "I love Akka streams", // odd
    "this has even characters", // even
    "this is amazing", // odd
    "learning Akka at the Rock the JVM", // odd
    "Let's rock the JVM", // even
    "123", // odd
    "1234" // even
  ))
  val totalCharCountFuture = textSource
    .groupBy(2, string => string.length % 2)
    .map { c =>
      println(s"I am running on thread [${Thread.currentThread().getId}]")
      c.length
    } .async // this operator runs in parallel
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  import system.dispatcher

  totalCharCountFuture.onComplete {
    case Success(value) => println(s"total char count: $value")
    case Failure(exception) => println(s"failed computation: $exception")
  }
}

