package learning.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object First_Principles_1 extends App {

  implicit val system = ActorSystem("FirstPrinciples-1")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
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