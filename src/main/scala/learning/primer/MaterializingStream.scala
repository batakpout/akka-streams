package learning.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
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
   val res: Source[Int, NotUsed] = source.reduce(_ +_)
   val directReduce: Future[Int] = source.runReduce(_ +_)
   val directFold: Future[String] = source.runFold(" ")((seed, cur) => seed + cur)
   Thread.sleep(1000)

   //API for backward support
   val back: NotUsed = Sink.foreach(println).runWith(Source.single(10)) // keep .left
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

   implicit val system = ActorSystem("FirstPrinciples-10")
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
object MaterializingStream_432 extends App {


      implicit val system = ActorSystem("FirstPrinciples-10")
      implicit val materializer = ActorMaterializer()


      val incSource: Source[Int, NotUsed] = Source(1 to 10)
      val incFlow: Flow[Int, String, NotUsed] = Flow[Int].map[String](x => x + "a")
      val graph1: Source[String, NotUsed] = incSource.via(incFlow) // default is toMat (Keep.left)
      val graph2: Source[String, NotUsed] = incSource.viaMat(incFlow)(Keep.right)
      val graph3: Source[String, NotUsed] = incSource.viaMat(incFlow)(Keep.left) // reason look at definition of toMat

}
object MaterializingStream_4 extends App {

  /*implicit implicit val system = ActorSystem("MaterializingStream-3")
   implicit val materializer = ActorMaterializer()

   val sentence = "The quick brown fox jumps over the lazy dog"
   val source: Source[String, NotUsed] = Source(sentence)

   val splitFlow: Flow[String, Int, NotUsed] = Flow[String].map[Int](_.split(" ").length)

   val printSink: Sink[Int, Future[Done]] = Sink.foreach(println)

   val sourceToSink: Source[Int, NotUsed] = source.via(splitFlow) //
   val graph = source.via(splitFlow).to(printSink)*/


}