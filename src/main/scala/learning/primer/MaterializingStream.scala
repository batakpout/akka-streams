package learning.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success}

/**
  * Fetching a meaningful value out of a running stream
  */

object Just_For_Understanding extends App {

   implicit val system = ActorSystem("FirstPrinciples-10")
   implicit val materializer = ActorMaterializer()

   val r = new Random(1)
   val source: Source[Int, NotUsed] = Source(1 to 100)
   val flow: Flow[Int, Float, NotUsed] = Flow[Int].map[Float](_ => r.nextFloat())

   /**
     *  definition of Source and Source.via which returns a Flow back
     *  final class Source[+Out, +Mat] // Out is what comes out of Stream and Mat is its Materialized value
     *  override def Source.via[T, Mat2](flow: Graph[FlowShape[Out, T], Mat2]): Repr[T] = viaMat(flow)(Keep.left)
     *  override type Repr[+O] = Source[O, Mat @uncheckedVariance]
     */
   val sourceWithFlow: Source[Float, NotUsed] = source.via[Float, NotUsed](flow) // this is keep.left
   /** Source.viaMat
     override def viaMat[T, Mat2, Mat3](flow: Graph[FlowShape[Out, T], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): Source[T, Mat3]
     */
   val graph4: Source[Float, NotUsed] = source.viaMat[Float, NotUsed, NotUsed](flow)(Keep.left)



   val reduceSink: Sink[Float, Future[String]] = Sink.fold[String, Float](" ")((res, cur) => cur + res)
   val reduceSin1k: Sink[Float, Future[Float]] = Sink.reduce[Float](_ + _)

   /**
     * Definition of Sink flow and Flow.to which returns a sink back
     * final class Flow[-In, +Out, +Mat](
     * def to[Mat2](sink: Graph[SinkShape[Out], Mat2]): Sink[In, Mat] = toMat(sink)(Keep.left)
     * def toMat[Mat2, Mat3](sink: Graph[SinkShape[Out], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): Sink[In, Mat3] =
     *  Mat2 means what is materialized value of to(Sink), and Mat3 means what will be the final materialized out of this sink
     * */
   val flowWithSink: Sink[Int, NotUsed] = flow.to[Future[String]](reduceSink) // coz to is Keep.left by default
   val flowWithSink2: Sink[Int, NotUsed] = flow.toMat[Future[String], NotUsed](reduceSink)(Keep.left)
   val flowWithSink1: Sink[Int, Future[String]] =  flow.toMat[Future[String], Future[String]](reduceSink)((flowOutput, sinkOutput ) => sinkOutput)

   val graph: RunnableGraph[NotUsed] = source.via(flow).to(reduceSink)
   val graph2: RunnableGraph[Future[String]] = source.via(flow).toMat(reduceSink)(Keep.right)
   val graph3: RunnableGraph[Future[String]] = source.viaMat(flow)(Keep.right).toMat(reduceSink)(Keep.right)

   //val rG: RunnableGraph[NotUsed] = sourceWithFlow.to(reduceSink)
}

object MaterializingStream_1 extends App {

   implicit val system = ActorSystem("MaterializingStream-1")
   implicit val materializer = ActorMaterializer()

   val source: Source[Int, NotUsed] = Source(1 to 10)
   val sink: Sink[Int, Future[Done]] = Sink.foreach(println)

   //normally when using via, to : left most materialized value is kept
   val rG: RunnableGraph[NotUsed] = source.to(sink)
   val result: NotUsed = rG.run() // materializing a stream

   Thread.sleep(1000)
   println("----1----")

   val reduceSink: Sink[Int, Future[Int]] = Sink.reduce(_ + _)
   val graph: RunnableGraph[Future[Int]] = source.toMat(reduceSink)(Keep.right)
   val sumFuture: Future[Int] = graph.run()

   import system.dispatcher
   sumFuture onComplete {
      case Success(v) => println(s"The sum of all element is :$v")
      case Failure(e) => println(s"The sum cannot be computed: ${e.getMessage}")
   }

   Thread.sleep(1000)

     //Also
   val keepsRightResult: Future[Int] = source.runWith(reduceSink) // source.toMat(sink)(Keep.right)
   val res: Source[Int, NotUsed] = source.reduce(_ +_) //Source.reduce keep.left default
   val directReduce: Future[Int] = source.runReduce(_ +_) //keep.right default
   val directFold: Future[String] = source.runFold(" ")((seed, cur) => seed + cur) //keep.right default
   Thread.sleep(1000)

   //API for backward support, ordering doesn't matter
   val back: NotUsed = Sink.foreach(println).runWith(Source.single(10)) // keep.right default
   //both ways
   val back2: (NotUsed, Future[Done]) = Flow[Int].map(_ + 1).runWith(source, sink)
}

object MaterializingStream_2 extends App {

   implicit val system = ActorSystem("MaterializedStream-2")
   implicit val materializer = ActorMaterializer()

   val source: Source[Int, NotUsed] = Source(1 to 10)
   val flow:Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 1)
   val sink: Sink[Int, Future[Done]] = Sink.foreach(println)


   val graph1: RunnableGraph[Future[Done]] = source.via(flow).toMat(sink)(Keep.right)

   val graphResult: Future[Done] = graph1.run()

   Thread.sleep(1000)
   println("----1----")
   val graph: RunnableGraph[Future[Done]] = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.right)

   import system.dispatcher
   graph.run().onComplete {
      case Success(_) => println("Stream processing finished")
      case Failure(exception) => println(s"Stream processing failed with : $exception")
   }
   Thread.sleep(2000)
}


object MaterializingStream_3 extends App {

   implicit val system = ActorSystem("FirstPrinciples-3")
   implicit val materializer = ActorMaterializer()

   val r = new Random(1)
   val source: Source[Int, NotUsed] = Source(1 to 100)
   val flow: Flow[Int, Float, NotUsed] = Flow[Int].map[Float](_ => r.nextFloat())

   val sourceWithFlow: Source[Float, NotUsed] = source.via(flow) // this is keep.left

   val graph4: Source[Float, NotUsed] = source.viaMat(flow)(Keep.right) // coz return type of Flow is NotUsed
   val graph5: Source[Float, NotUsed] = source.viaMat(flow)(Keep.left) // coz return type of Flow is NotUsed



   val reduceSink: Sink[Float, Future[String]] = Sink.fold[String, Float](" ")((res, cur) => cur + res)
   val reduceSin1k: Sink[Float, Future[Float]] = Sink.reduce[Float](_ + _)


   val flowWithSink: Sink[Int, NotUsed] = flow.to(reduceSink) // coz to is Keep.left by default
   val flowWithSink2: Sink[Int, NotUsed] = flow.toMat(reduceSink)(Keep.left)
   val flowWithSink1: Sink[Int, Future[String]] =  flow.toMat(reduceSink)((flowOutput, sinkOutput ) => sinkOutput)
   val flowWithSink3: Sink[Int, Future[String]] =  flow.toMat(reduceSink)(Keep.right)

   val graph: RunnableGraph[NotUsed] = source.via(flow).to(reduceSink)

   val graph2: RunnableGraph[Future[String]] = source.via(flow).toMat(reduceSink)(Keep.right)
   val graph3: RunnableGraph[Future[String]] = source.viaMat(flow)(Keep.right).toMat(reduceSink)(Keep.right)

}
object MaterializingStream_4 extends App {


      implicit val system = ActorSystem("FirstPrinciples-4")
      implicit val materializer = ActorMaterializer()


      val incSource: Source[Int, NotUsed] = Source(1 to 10)
      val incFlow: Flow[Int, String, NotUsed] = Flow[Int].map[String](x => x + "a")
      val graph1: Source[String, NotUsed] = incSource.via(incFlow) // default is toMat (Keep.left)
      val graph2: Source[String, NotUsed] = incSource.viaMat(incFlow)(Keep.right)
      val graph3: Source[String, NotUsed] = incSource.viaMat(incFlow)(Keep.left) // reason look at definition of toMat

}
object MaterializingStream_5 extends App {

   implicit val system = ActorSystem("MaterializingStream-5")
   implicit val materializer = ActorMaterializer()

   val sentence = List("The quick brown fox jumps over the lazy dog")
   val source: Source[String, NotUsed] = Source(sentence)

   val splitFlow: Flow[String, Int, NotUsed] = Flow[String].map[Int](_.split(" ").length)
   val reduceFlow: Flow[Int, Int, NotUsed] = Flow[Int].reduce(_ +_)
   val printSink: Sink[Int, Future[Done]] = Sink.foreach(println)
   val reduceSink:Sink[Int, Future[Int]] = Sink.reduce(_ + _)

   val graphX = source
     .viaMat(splitFlow)(Keep.right)
     .viaMat(reduceFlow)(Keep.right)
     .toMat(reduceSink)(Keep.right)

   val result: Future[Int] = Akka_Stream_Utils.execute(graphX)

   import system.dispatcher
   result.onComplete {
      case Success(s) => println(s"result: $s")
      case Failure(e) => println(s"failure: $e")
   }

   Thread.sleep(1000)

   //keeping both values

   val result2: RunnableGraph[((NotUsed, NotUsed), Future[Int])] =
      source.viaMat(splitFlow)(Keep.both).toMat(reduceSink)(Keep.both)

   val graph1: RunnableGraph[Future[Done]] = source.viaMat(splitFlow)(Keep.right).
            toMat(printSink)(Keep.right)

}

object MaterializingStream_6 extends App {

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

   Thread.sleep(1000)

   val f2: Future[Int] = Source(1 to 10).runWith(Sink.head)

   f2.onComplete {
      case Success(e) => println(e)
      case Failure(e) => println(e.toString)
   }

   val f3: Future[Int] = Source(1 to 10).runWith(Sink.last)
   f3.onComplete {
      case Success(e) => println(e)
      case Failure(e) => println(e.toString)
   }

}

object MaterializingStream_7 extends App {

   implicit val system = ActorSystem("MaterializingStreams-7")
   implicit val materializer = ActorMaterializer()

   val sentenceSource = Source(List(
      "Akka is awesome",
      "I love streams",
      "Materialized values are killing me"
   ))

   //Sink.fold is Keep.right
   val wordCountSink: Sink[String, Future[Int]] = Sink.fold[Int, String](0)((acc, cur_value) => acc + cur_value.split(" ").length)

   val g1 = sentenceSource.runWith(wordCountSink)
   println( Await.result(g1, 1.seconds))
   Thread.sleep(1000)

   //or
   val g2 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
   println( Await.result(g2, 1.seconds))
   Thread.sleep(1000)

   val g3: Future[Int] = sentenceSource.runFold(0)((acc, cur_value) => acc + cur_value.split(" ").length)
   println( Await.result(g3, 1.seconds))
   Thread.sleep(1000)
}

object MaterializingStream_8 extends App {
   implicit val system = ActorSystem("MaterializingStreams-8")
   implicit val materializer = ActorMaterializer()

   val sentenceSource = Source(List(
      "Akka is awesome",
      "I love streams",
      "Materialized values are killing me"
   ))

   val wordCountFlow: Flow[String, Int, NotUsed] = Flow[String].fold[Int](0)((acc, cur_value) => acc + cur_value.split(" ").length)

   val g1: Future[Int] = sentenceSource.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()
   println( Await.result(g1, 1.seconds))
   Thread.sleep(1000)
   val g5 = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
   println( Await.result(g5, 1.seconds) )
   Thread.sleep(1000)
   val g6 = sentenceSource.via(wordCountFlow).runWith(Sink.head)
   println( Await.result(g6, 1.seconds) )
   Thread.sleep(1000)
   val g7 = wordCountFlow.runWith(sentenceSource, Sink.head)._2
   println(Await.result(g7, 1.seconds))

}