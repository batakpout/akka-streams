package learning.techniques_patterns

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import from_daniel.part4_techniques.AdvancedBackpressure.{Notification, PagerEvent, oncallEngineer, sendEmail}

import java.util.Date

/**
  * Buffers to control rate of the flow of elements inside an akka stream
  * How to cope with an un-backpressureable source
  * How to deal with fast consumer with Extrapolating/expanding
  */
object AdvancedBackPressures_1 extends App {

  implicit val system = ActorSystem("AdvancedBackpressure")
  implicit val materializer = ActorMaterializer()

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstance:Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the data pipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date)
  )
  val eventSource = Source(events)

  val oncallEngineer = "daniel@rockthejvm.com" // a fast service for fetching oncall emails

  def sendEmail(notification: Notification) =
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}")  // actually send an email

  val notificationSink = Flow[PagerEvent].map(event => Notification(oncallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  // standard
  //  eventSource.to(notificationSink).run()

  /**
    * Un-backpressureable source
    * Let's say this sendEmailSlow service is very very slow, in that case the notificationSink will normally try to
      backpressure the source, this might cause issue. 1. on the practical side, engineers may not get paged on time,
      2.) for akka stream considerations, this Source may not be able to backpressure, especially if its timer based, so
       timer based Sources don't respond to backpressure.
    * So if the Source cannot be back-pressured, for some reason, the solution to that, is to somehow aggregate the
      in-comming events and create a one single notification, when we receive demand from Sink. Instead of buffering the
      events on our flow, we can create a single notification for multiple events. So we will aggregate the events, and
      will create a single notification out of them.
    */
  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}") // actually send an email
  }

  //acts like fold, aggregate but emits when demand from downstream
  /** conflate
    * Allows a faster upstream to progress independently of a slower subscriber by conflating elements into a summary
    * until the subscriber is ready to accept them
    */
  private val aggregateNotificationFlow = Flow[PagerEvent].conflate((event1, event2) => {
    val nInstances = event1.nInstance + event2.nInstance
    PagerEvent(s"You have $nInstances events that requires your attention", new Date, nInstances)
  })//.map(resultingEvent => Notification(oncallEngineer, resultingEvent))

   eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[PagerEvent](println)).run()
  // alternative to backpressure
}