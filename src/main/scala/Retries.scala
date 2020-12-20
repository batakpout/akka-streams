import akka.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
object Retries1 extends App {


  implicit val system = ActorSystem("MaterializingStreams")

  implicit val scheduler = system.scheduler
  //Given some future that will succeed eventually
  @volatile var failCount = 0
  def attempt() = {
    if (failCount < 5) {
      println("failed retrying....")
      failCount += 1
      Future.failed(new IllegalStateException(failCount.toString))
    } else Future.successful(5)
  }
  //Return a new future that will retry up to 10 times
  val retried = akka.pattern.retry(() => attempt(), 10, 5.second)
}