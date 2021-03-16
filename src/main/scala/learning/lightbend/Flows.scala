package learning.lightbend

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.collection.immutable
import scala.concurrent.duration._

object Flows_1 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val source = Source(1 to 10)
  val sink = Sink.foreach[Int](println)

  /**
    * mapConcat:
    * transforms data into a collection tht is "flattened" into the stream.
    * Similar to flatMap on a collection
    */

  //val flow: Flow[String, Vector[String], NotUsed] = Flow[String].map(str => str.split("\\s").toVector)

  val stringSource = Source(List("Welcome to the shehzal corp", "the new org i am about to commence"))
  val flow: Flow[String, String, NotUsed] = Flow[String].mapConcat(str => str.split("\\s").toVector).log("MyFlowTag")
  stringSource.via(flow).to(Sink.foreach[String](println)).run()

}

object Flows_2 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val source = Source(1 to 100)

  val sink = Sink.foreach[Seq[Int]](println)

  val groupsOf10: Flow[Int, Seq[Int], NotUsed] = Flow[Int].grouped(10)
  val slidingWindowOf10: Flow[Int, Seq[Int], NotUsed] = Flow[Int].sliding(10, 1)

  //source.via(groupsOf10).to(sink).run()
  source.via(slidingWindowOf10).to(sink).run()


}

object Flows_3 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val source = Source(1 to 100000)
  val sink = Sink.foreach[Int](println)
  val sinkSeq = Sink.foreach[Seq[Int]](println)

  /**
    * takeWithin: take elements from stream for the given duration, then terminate.
    * dropWithin: drop data for a specified amount of time, then proceed with the rest.
    * groupedWithin; group elements for the given number or time period, whichever comes first.
    */
  //val f: Flow[Int, Int, NotUsed] = Flow[Int].takeWithin(1.second)
  //val f1: Flow[Int, Int, NotUsed] = Flow[Int].dropWithin(1.second)

  //first pulls
  val f2: Flow[Int, immutable.Seq[Int], NotUsed] = Flow[Int].groupedWithin(100, 1.micro) // first ran
  source.via(f2).to(sinkSeq).run()

}

object Flows_4 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val sink = Sink.foreach[(String, Int)](println)


    val source = Source(List("The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"))

  /**
    * Combines the in-comming elements with elements from another Source.
    * Result is emitted as tuple of both values.
    * When flow terminates the internal Source terminates too.
    */
  val flow: Flow[String, (String, Int), NotUsed] = Flow[String].zip({
    Source.fromIterator(() => Iterator.from(0))
  })

    source.via(flow).to(sink).run()

}

object FlatMapping_Flow extends App {

  implicit val system = ActorSystem()
  implicit val materilaizer = ActorMaterializer()


  val simpleSource = Source(List(1, 2))
  val s1: Source[Int, NotUsed] = simpleSource.flatMapConcat(x => Source(x to (3 * x)))
  s1.runWith(Sink.foreach(println))
  println("----")
  //simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}

object Flows_5 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val sink = Sink.foreach[Int](println)

  val source = Source.single(1)

  val flow1 : Flow[Int, Source[Int, NotUsed], NotUsed] = Flow[Int].map(i => Source.fromIterator(() => Iterator.from(i)))

  val flow2: Flow[Int, Int, NotUsed] = Flow[Int].flatMapConcat(i => Source.fromIterator(() => Iterator.from(i)))

  //source.via(flow2).runWith(sink)

  /**
    * in flatMapConcat,
    * like mapConcat, but operates on Sources , rather than Iterables
    * sub-streams are consumed in sequence which preserves ordering.
    * Doesn't terminate till inner Source doesn't terminate
    */

  /**
    * flatMapMerge:
    *  like mapConcat, but sub-streams are consumed simultaneously, Order therefore is not guaranteed.
    *  Breadth indicates how many sub-streams to consume at a time.
    *  Doesn't terminate till inner Source doesn't terminate
    */
  val flow3: Flow[Int, Int, NotUsed] = Flow[Int].flatMapMerge(breadth = 2, i => Source.fromIterator(() => Iterator.from(0)))
  source.via(flow3).runWith(sink)
}

object Flows_6 extends App {


    implicit val system = ActorSystem("DSH-1")
    implicit val materializer = ActorMaterializer()

    val s = Source(1 to 10)
    val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2).log("MyFlowTag")
    s.via(flow).to(Sink.foreach[Int](println)).run()
}