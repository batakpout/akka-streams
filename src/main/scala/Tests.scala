import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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

object TestStream extends App {

  implicit val system = ActorSystem("FT1")
  implicit val materializer = ActorMaterializer()

  val source = Source(List(1, 5, 13))
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int] {
    case 13 => throw new RuntimeException("triskaidekaphobia")
    case x => println(x)
  }

  val res: (NotUsed, Future[Done]) = source.toMat(sink)(Keep.both).run()

  res._2.onComplete {
    case Success(value) => {
      println("cane gere")
      println(value)
    }
    case Failure(e) => {
      println("came bear")
      println(e.getMessage)
      println(e.getLocalizedMessage)
      println(e.printStackTrace())
    }
  }
  Thread.sleep(3000)
}

object ImplicitTest extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit class TimedFuture[A](future: Future[A]) {
    def withTimer(name: String): Future[A] = {

      val startTime = System.currentTimeMillis()
      future.andThen {
        case _ =>
          val endTime = System.currentTimeMillis()
          println(s"$name completed in ${endTime - startTime}ms")
      }
    }
  }

  val result = Future {
    Thread.sleep(5000)
    10
  }

  result.withTimer("timer")

  Thread.sleep(200000)
}