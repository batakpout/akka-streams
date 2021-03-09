package learning.techniques_patterns

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt


object IntegrationWithActor_1 extends App {

  /**
    * So here Actor works as a flow
    */
  implicit val system = ActorSystem("IntegrationWithActors1")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numberSource = Source(1 to 10)

  implicit val timeOut = Timeout(2.seconds)

  /**
    * Parallelism limits the number of how many asks can be "in flight" at the same time.
    * or parallelism = 4, how many messages can be in the mailbox of this simple actor before it
    * will start back-pressuring its source.
    */
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  numberSource.via(actorBasedFlow).to(Sink.ignore).run()

  numberSource.ask[Int](parallelism = 4)(simpleActor).runWith(Sink.ignore) //equivalent

}

object IntegrationWithActor_2 extends App {

  /**
    * So here Actor works as a Source
    * Here, actorRef is exposed to us, so that we can send messages to it, and when we send a message to this
    * actorRef, it means we are injecting a value into the stream.
    */

  implicit val system = ActorSystem("IntegrationWithActors1")
  implicit val materializer = ActorMaterializer()

  val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)

  val materializedActorRef: ActorRef = actorPoweredSource.to(Sink.foreach(number => println(s"Actor powered flow got number: $number"))).run()

  materializedActorRef ! 10
  materializedActorRef ! akka.actor.Status.Success("complete")
}

object IntegrationWithActor_3 extends App {

  /**
    * So here Actor works as a Sink
    * Actor powered Sink will need to support some special signals:
    * 1. init message: which will be sent first by whichever component ends up connecting to this Sink
    * 2. ack message: which is send from this actor to stream to confirm the reception on an element, absence of this
    *  ack message will be interpreted as back-pressure.
    * 3. complete message
    * 4. a function to generate a message in case the stream throws an exception
    */

  case object StreamInit

  case object StreamAck

  case object StreamComplete

  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case StreamInit => log.info("Stream Initialized...")
        sender() ! StreamAck

      case StreamComplete =>
        log.info("Stream Complete...")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message has come to its final resting point")
        sender() ! StreamAck //commenting this will start back-pressure

    }
  }

  implicit val system = ActorSystem("IntegrationWithActors1")
  implicit val materializer = ActorMaterializer()

  val destinationActor = system.actorOf(Props[DestinationActor], "DestinationActor")

  //some lifecycle messages.
  val actorPoweredSink: Sink[Int, NotUsed] = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete, //when stream completes
    ackMessage = StreamAck,
    onFailureMessage = throwable => StreamFail(throwable) // optional, if stream fails
  )

  val source = Source(1 to 10)
  source.runWith(actorPoweredSink)

  val another = Sink.actorRef(destinationActor, StreamComplete) // no back - pressure

}