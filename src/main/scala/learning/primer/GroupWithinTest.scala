package learning.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.Future

object GroupWithinTest_1 extends App {

  implicit val system = ActorSystem("Test1")
  implicit val materializer = ActorMaterializer()

  val counter = new AtomicInteger(0)
  Source
    .tick(initialDelay = 0.seconds, interval = 1.seconds, tick = ())
    .map { _ =>
      counter.incrementAndGet()
    }
    .groupedWithin(10, 10.seconds)
    .mapAsync(1) { result =>
      println("results :" +result.toList)
      Future.successful(result)
    }
    .runWith(Sink.ignore)


}

object SomeTest extends App {
  implicit val system = ActorSystem("Test1")
  implicit val materializer = ActorMaterializer()

  val counter = new AtomicInteger(0)

  Source
    .tick(initialDelay = 0.seconds, interval = 1.seconds, tick = ())
    .map { _ =>
      counter.incrementAndGet()
    }.to(Sink.foreach[Int](println)).run()
}