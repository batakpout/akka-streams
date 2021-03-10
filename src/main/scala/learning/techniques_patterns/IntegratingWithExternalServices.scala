package learning.techniques_patterns

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import java.util.Date
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future

object IntegratingWithExternalServices extends App {

  implicit val system = ActorSystem("IWES")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher // not recommended in practice for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher") // configure it

  //example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource: Source[PagerEvent, NotUsed] = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeLine", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontEnd", "A button doesn't work", new Date)
  ))

  object PagerService {

    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@gmail.com",
      "John" -> "john@yahoo.com",
      "Lady Gaga" -> "ladygaga@rediff.com"
    )

     def processEvent(pagerEvent: PagerEvent) = Future {
      //logic to get values 0, 1, 2 based on number of days till that pager event date
      //each engineer out of 3 will get all pageEvents on one day i.e give issue to one engineer for one day and cycle.
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)
      //return the email that was paged
      engineerEmail
    }
  }
  //let's say I am interested in AkkaInfra events. i.e
  val infraEvents: Source[PagerEvent, NotUsed] = eventSource.filter(_.application == "AkkaInfra")
  //val x: Source[Future[String], NotUsed] = infraEvents.map(pagerEvent => PagerService.processEvent(pagerEvent)) // can't use regular flow, Futures here
  //mapAsync: guarantees the relative order of elements from up to down stream, see also documentation.
  //mapAsyncUnordered, doesn't guarantee order
  /**
    -val pagedEngineerEmails: Source[String, NotUsed] = infraEvents.mapAsync(parallelism = 1)(pagerEvent => PagerService.processEvent(pagerEvent))
    single threaded, there will be lag for sending emails, with parallelism - 4 increases throughput or performance of our akka steam.
    Also, mapAsyncUnordered will be fast as there is no ordering and no wait for 1st future to finish.
  */

  val pagedEngineerEmails: Source[String, NotUsed] = infraEvents.mapAsync(parallelism = 4)(pagerEvent => PagerService.processEvent(pagerEvent))
  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully send notification to $email"))
  //pagedEngineerEmails.to(pagedEmailsSink).run() // no delay of 1 sec for processEvent to execute as parallelism = 4

  //now ask engineer to look into this
  /**
    * Since we may end up running lot of Futures, we should run them in their own ExecutionContext,
      not on the ActorSystem's dispatcher because we may starve it for threads.
    */

  /**
    * Since this mapAsync works well with Future, it will work very nicely with asking actors
    */

  class PagerActor extends Actor with ActorLogging {
      private val engineers = List("Daniel", "John", "Lady Gaga")
      private val emails = Map(
        "Daniel" -> "daniel@gmail.com",
        "John" -> "john@yahoo.com",
        "Lady Gaga" -> "ladygaga@rediff.com"
      )

    override def receive: Receive = {
      case pagerEvent: PagerEvent => sender() ! processEvent(pagerEvent)
    }

      private def processEvent(pagerEvent: PagerEvent): String = {
        //logic to get values 0, 1, 2 based on number of days till that pager event date
        //each engineer out of 3 will get all pageEvents on one day i.e give issue to one engineer for one day and cycle.
        val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
        val engineer = engineers(engineerIndex.toInt)
        val engineerEmail = emails(engineer)
        println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
        Thread.sleep(1000)
        //return the email that was paged
        engineerEmail
      }

  }

  val pagerActor = system.actorOf(Props[PagerActor], "PagerActor")
  import scala.concurrent.duration._
  implicit val timeOut = Timeout(4 seconds)
  val alternativePageEngineerEmails: Source[String, NotUsed] = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePageEngineerEmails.to(pagedEmailsSink).run()
  /**
    * There is delay of 1 sec even if parallelism = 4 because , its ask pattern, it will go into actor, Thread.sleep 1s makes it wait for 1s,
       then only it will process the second message
    */

  /**
    * Do not confuse mapAsync with async (async is ASYNC boundary, which makes a chain or component to run on separate actor)
    */

  /**
    * The global Execution context (scala.concurrent.ExecutionContext.Implicits.global) spawns a thread pool with
      threads equal to the number of cores in your machine. The system's dispatcher (system.dispatcher) also implements
      the ExecutionContext trait but it does additional things (like scheduling actors for execution)
    */

  /**
    * For persistence, it depends what you'd like to persist. If you want to store everything that goes through the stream,
      you can do that by creating a custom Flow  and sending the items to a persistent actor.
    */
}