package learning.primer

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

object OperatorFusion1 extends App {

   implicit val system = ActorSystem("OperatorFusion")
   implicit val materializer = ActorMaterializer()

   val simpleSource = Source(1 to 10)
   val simpleFlow1 = Flow[Int].map[Int](_ + 1)
   val simpleFlow2 = Flow[Int].map[Int](_ * 10)
   val simpleSink = Sink.foreach[Int](println)

  //the composite akka stream by default run on the same actor: Operator/Component Fusion
  //this runs on the same actor

  val s: RunnableGraph[NotUsed] = simpleSource.via(simpleFlow1).via(simpleFlow2).to(simpleSink)
 // simpleSource.via(simpleFlow1).via(simpleFlow2).to(simpleSink).run()

  //def run()(implicit materializer: Materializer): Mat = materializer.materialize(this)

  //above RunnableGraph is same as
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        val y = x + 1
        val z = y * 10
        println(z)
    }
  }
  val actorRef = system.actorOf(Props[SimpleActor], "simpleactor")
  //(1 to 10).foreach(actorRef ! _)
  /** Operator Fusion is nice because normally there is a time overhead for the async messages passing between the actors of each of these
    * akka stream components which takes a while.
    * If operation inside components are good then operator fusion is good, that is what akka streams does by default
    * So, when the operations inside these components are quick then operation fusion is good. otherwise,
    *
    */



}

object OperatorFusion2 extends App {

  implicit val system = ActorSystem("OperatorFusion")
  implicit val materializer = ActorMaterializer()

  val simpleSource = Source(1 to 10)
  val simpleFlow1 = Flow[Int].map[Int](_ + 1)
  val simpleFlow2 = Flow[Int].map[Int](_ * 10)
  val simpleSink = Sink.foreach[Int](println)


  val complexFlow1 = Flow[Int].map[Int] { x =>
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map[Int] { x =>
    Thread.sleep(1000)
    x + 1
  }

  //simpleSource.via(complexFlow1).via(complexFlow2).to(simpleSink).run()

  /**
    * Since the whole point of akka streams is to process asynchronously in between these components,
    * We would like to increase this throughput, because there is room for improvement.
    * So, when operatives are time expensive, i.e takes time, then it's worth making them run separately in parallel on  different actor
    */

  //Async boundaries
  simpleSource.via(complexFlow1).async // runs on one actor
    .via(complexFlow2).async //runs on another actor
    .to(simpleSink)
    .run()// runs on another actor
  /**
    * So, we are able to pipeline these complex/expensive operations and run them in parallel.
    * So, we are basically able to double our throughput.
    * So, these async calls are called async boundaries.
    * We can introduce as many async boundaries as we want.
    * Communication between async boundaries is done via asynchronous actor messages
    * Technically, an async boundary contains:
    * source -> compleFlow1 | complexFlow2 | sink
    *       async boundary  |   actor2     | actor3
    *<_ ________actor1 _____|              |
    *                       |async boundary|
    *                       |              |
    * ______________________|______________|
    */

  /**
    * we should avoid using async boundaries when operations are comparable with a message pass.
    * In other words, you don't need an async boundary when an operation is trivial,
    * because if you insert an async boundary, you introduce a time overhead to feed elements to a new actor.
    * If it takes just as much time to process an element as it takes to pass that element to the actor,
    * you're not improving the performance, you're making it worse.
   */

}
object OperatorFusion3 extends App {

  implicit val system = ActorSystem("OperatorFusion")
  implicit val materializer = ActorMaterializer()

  /**    Now, order guarantees with or without async boundaries
    *   Every element is fully processed in the stream before any new element is admitted in the stream.
    */
  Source(1 to 3).
    map { elem => println(s"Flow A : $elem"); elem}.
    map { elem => println(s"Flow B : $elem"); elem}.
    map { elem => println(s"Flow C : $elem"); elem}.
    runWith(Sink.ignore)


}

object OperatorFusion4 extends App {

  implicit val system = ActorSystem("OperatorFusion")
  implicit val materializer = ActorMaterializer()

  /**
    * Now, introducing async boundaries.
    * So using async boundary we won't be having same strong ordering guarantees but we do have one ordering guarantee,
    * i.e the relative ordering of these elements inside each step of the stream will be guaranteed.
    */
  Source(1 to 3).
    map { elem => println(s"Flow A : $elem"); elem}.async.
    map { elem => println(s"Flow B : $elem"); elem}.async.
    map { elem => println(s"Flow C : $elem"); elem}.async.
    runWith(Sink.ignore)


}