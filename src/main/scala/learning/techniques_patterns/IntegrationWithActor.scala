package learning.techniques_patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.util.Timeout

import scala.concurrent.duration.DurationInt


object IntegrationWithActor_1 extends App {

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
       will start back-pressuring its source.
    */
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

}