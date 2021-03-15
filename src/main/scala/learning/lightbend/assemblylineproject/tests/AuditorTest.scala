package learning.lightbend.assemblylineproject.tests

import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.EventFilter
import learning.lightbend.assemblylineproject.common._
import learning.lightbend.assemblylineproject.main._
import org.scalatest.WordSpecLike

import scala.collection.immutable.Seq
import scala.concurrent.duration._

class AuditorTest extends WordSpecLike with AkkaSpec {

  "count" should {
    "should return zero if the stream is empty" in {
      val auditor = new Auditor

      val count = Source.empty[Int].runWith(auditor.count).futureValue

      assert(count === 0)
    }
    "should count elements in the stream" in {
      val auditor = new Auditor

      val count = Source(1 to 10).runWith(auditor.count).futureValue

      assert(count === 10)
    }
  }

  "log" should {
    "should log nothing if the source is empty." in {
      implicit val adapter = system.log
      val auditor = new Auditor

      EventFilter.debug(occurrences = 0).intercept {
        Source.empty[Int].runWith(auditor.log)
      }
    }
    "should log all elements to the logging adapter." in {
      implicit val adapter = system.log
      val auditor = new Auditor

      EventFilter.debug(occurrences = 10).intercept {
        Source(1 to 10).runWith(auditor.log)
      }
    }
    "should log the exact element" in {
      implicit val adapter = system.log
      val auditor = new Auditor

      EventFilter.debug(occurrences = 1, message = "Message").intercept {
        Source.single("Message").runWith(auditor.log)
      }
    }
  }

  "sample" should {
    "should do nothing if the source is empty" in {
      val auditor = new Auditor
      val sampleSize = 100.millis

      val source = Source.empty[Car]

      source.via(auditor.sample(sampleSize))
        .runWith(TestSink.probe[Car])
        .request(10)
        .expectComplete()
    }
    "should return all elements if they appear within the sample period." in {
      val auditor = new Auditor
      val sampleSize = 100.millis
      val expectedCars = 10

      val (source, sink) = TestSource.probe[Car]
        .via(auditor.sample(sampleSize))
        .toMat(TestSink.probe[Car])(Keep.both)
        .run()

      sink.request(20)

      (1 to expectedCars).foreach(_ => source.sendNext(
        Car(SerialNumber(), Color("000000"), Engine(), Seq.fill(4)(Wheel()), None)
      ))
      source.sendComplete()

      sink.expectNextN(expectedCars)
      sink.expectComplete()
    }
    "should ignore elements that appear outside the expected sample period." in {
      val auditor = new Auditor
      val sampleSize = 100.millis
      val expectedCars = 3

      val (source, sink) = TestSource.probe[Car]
        .via(auditor.sample(sampleSize))
        .toMat(TestSink.probe[Car])(Keep.both)
        .run()

      sink.request(20)

      (1 to expectedCars).foreach(_ => source.sendNext(
        Car(SerialNumber(), Color("000000"), Engine(), Seq.fill(4)(Wheel()), None)
      ))
      Thread.sleep(sampleSize.toMillis * 2)

      source.sendNext(
        Car(SerialNumber(), Color("000000"), Engine(), Seq.fill(4)(Wheel()), None)
      )
      source.sendComplete()

      sink.expectNextN(expectedCars)
      sink.expectComplete()
    }
  }
}
