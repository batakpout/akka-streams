package learning.advanced

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.{Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph, KillSwitches, SharedKillSwitch, UniqueKillSwitch}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * Stop or abort a stream at runtime intentionally when its running.
  * Dynamically add fan-in/fan-out branches to some components.
  */

object DynamicStreamHandling_1 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  // #1: Kill Switch

  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int]
  val counter = Source(Stream.from(1)).throttle(1, 1.second).log("counter")
  val sink = Sink.ignore

  //killSwtich as the materialized value
  //UniqueKillSwitch -> from KillSwitches.single, capability of killing a single stream
  //val killSwitch: UniqueKillSwitch = counter.viaMat(killSwitchFlow)(Keep.right).toMat(sink)(Keep.left).run()

  import system.dispatcher

  // after 3 seconds the stream is terminated
  /*  system.scheduler.scheduleOnce(3.seconds) {
      killSwitch.shutdown()
    }*/

  //SharedKillSwitch can kill multiple streams at once

  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anothercounter")
  val sharedKillSwitch: SharedKillSwitch = KillSwitches.shared("OneButtonToRuleThemAll")
  //sharedKillSwitch acts in a different way, we don't need its materialized value.

  val r1: Future[Done] = counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  //val r: RunnableGraph[SharedKillSwitch] = counter.viaMat(sharedKillSwitch.flow)(Keep.right).toMat(Sink.ignore)(Keep.left)
  val r2: Future[Done] = anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)


  /* system.scheduler.scheduleOnce(5.seconds) {
    //this will shut down both the streams
     sharedKillSwitch.shutdown() // shutdown successfully
   }*/

  system.scheduler.scheduleOnce(5.seconds) {
    //this will shut down both the streams
    //sharedKillSwitch.shutdown()
    sharedKillSwitch.abort(new Exception(" I kill I kill I got I got")) // abort with a throwable
  }
}

object DynamicStreamHandling_2 extends App {

  //Dynamically add fan-in/fan-out branches to some components.

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  //MergeHub, fan in
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]

  //materialized sink will have same sink always
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()

  //====little confusion======
//  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
 // val normally: RunnableGraph[NotUsed] = Source(1 to 10).to(Sink.foreach[Int](println))
  //val res: NotUsed= Source(1 to 10).to(Sink.foreach[Int](println)).run
  //val xx: Future[Done] = Source(1 to 10).runWith(sink)
  //====little confusion ends======

  // now running dynamicMerge with a consumer i.e Sink produced a materialized value which we can plug to other components.

  //use this sink anytime we like,materializedSink
 // val x: NotUsed = Source(1 to 10).runWith(materializedSink)
  // so here we plugged Source(1 to 10) to Sink.foreach[Int](println), so we can plug different source to same consumer Sink.foreach[Int](println)
  // Advantage of this technique is that it can be programmed to run while the stream is active unlike e.g the GraphDSL

 val dynamicBroadcast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
 val s = Source(1 to 3)
  val rs = s.to(dynamicBroadcast)
  //since its a sink we need to plug it to the same producer.

  // materializedSource will have same source always
  val materializedSource: Source[Int, NotUsed] = Source.empty.runWith(dynamicBroadcast).log("xxx")

   //val f: Future[Int] = materializedSource.toMat(Sink.reduce[Int](_ + _))(Keep.right).run()

  val c = Source(1 to 10).concat(materializedSource).runWith(Sink.foreach[Int](println))

  import scala.concurrent.Await
  //val r = Await.result(f, 2.seconds)
  //println("===" + r)






}

object DynamicStreamHandling_3 extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()


  val dynamicBroadcast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
  val materializedSource: Source[Int, NotUsed] = Source(12 to 22).runWith(dynamicBroadcast)


  /*
    Combine a merge hub and a broad cast hub,any uses for it.
    A publisher subscriber component, in that we can dynamically add sources, sinks to this component,
    and every single element produced by every single producer will be know by every single subscriber. connect

   */

  val merge: Source[String, Sink[String, NotUsed]] = MergeHub.source[String]
  val bcast: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]

  val (sinkFromMerge, sourceFromBroadcast)= merge.toMat(bcast)(Keep.both).run()

  sourceFromBroadcast.runWith(Sink.foreach(x => println(s"I received $x")))
  sourceFromBroadcast.map(x => x.length).runWith(Sink.foreach[Int](x => println(s"I got a number: $x")))

  Source(List("Akka", "Stream", "is", "cool")).runWith(sinkFromMerge)

  Source.single("other source singly").runWith(sinkFromMerge)




}

object SomeQuestion extends App {

  implicit val system = ActorSystem("DSH-1")
  implicit val materializer = ActorMaterializer()

  def dynamicStreams(implicit as: ActorSystem): Unit = {

    def describe[A](description: String)(thunk : => A)(implicit ec: ExecutionContext): Unit = {
      println(s"---------- [ $description ] ")
      val _ = thunk
      Thread.sleep(1500)
      println("----------")
    }
    implicit val ec: ExecutionContext = as.dispatcher

    val merge = MergeHub.source[Int]
    val broadcast = BroadcastHub.sink[Int]

    val (publisherPort, subscriberPort) = merge.toMat(broadcast)(Keep.both).run()


    describe("nothing") {
      Source(1 to 3).runWith(publisherPort)
    }

    describe("triggers consumption 1, 2, 3") {
      subscriberPort.runWith(Sink.foreach(println))
    }
  }

  dynamicStreams

  /**
    Description:

     At the moment when you feed the elements 1,2,3 into the stream, the broadcast hub has nowhere to send the elements to,
     so it will buffer them. When you then connect the broadcast hub to an actual sink, it will release the elements.
    */
}