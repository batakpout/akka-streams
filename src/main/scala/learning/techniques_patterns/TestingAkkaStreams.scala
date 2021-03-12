package learning.techniques_patterns

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.RecoverMethods.recoverToSucceededIf
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
  * Unit testing akka streams:
  * - assertions on final results
  * - integrating with test actors
  * - using special akka streams test-kits
  */
class TestingAkkaStreamsSpec extends TestKit(ActorSystem("TestingAkkaStreams"))
  with WordSpecLike with BeforeAndAfterAll {

  implicit val materilaizer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream " should {
    "satisfy basic assertions" in {

      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)

    }

    "integrate with test actors via materialized values" in {
      import system.dispatcher
      import akka.pattern.pipe

      val probe: TestProbe = TestProbe()
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)
      probe.expectMsg(55)

    }

    "integrate with a test-actor-based sink" in {

      val simpleSource = Source(1 to 5)
      val flow = Flow[Int].scan(0)(_ + _)
      val streamUnderTest = simpleSource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completionMessage")

      streamUnderTest.to(probeSink).run

      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with stream TestKit Sink" in {
      val sourceUnderTest = Source(1 to 4).map(_ * 2)

      val testSink: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]
      val matTestValue: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(testSink)

      //      matTestValue.
      //        request(2).
      //        expectNext(2, 4)
      //expectComplete(), it won't be fulfilled, as stream is not finished yet

      matTestValue.
        request(4).
        expectNext(2, 4, 6, 8).
        expectComplete() // after this stream should finish
    }

    "integrate with stream TestKit Source" in {
      val sinkUnderTest: Sink[Any, Future[Done]] = Sink.foreach {
        case 13 => throw new RuntimeException
        case _ =>
      }
      val testSource: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
      val (testPublisher, resultFuture) = testSource.toMat(sinkUnderTest)(Keep.both).run()

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      import system.dispatcher
      resultFuture.onComplete {
        case Success(_) => fail("the sink under test should have thrown an exception on 13")
        case Failure(_) => //ok
      }
      //if no number 13 in source, still test passes
      //It's likely because the future was completed after the test finished.
    }

    "integrate flows with a test source AND a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val (testPublisher, testSubscriber) = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()

      testSubscriber
        .request(4)
      testSubscriber.expectNext(2, 10, 84, 198)
        .expectComplete()
    }

   "some random future test" in {
     /**
       * How do you assert a Future returned with an Exception. i.e
       * How do you make this test case pass asserting for Exception
       */
     val simpleSource = Source(1 to 10)
     val simpleSink = Sink.fold(0)((a: Int, b: Int) => {
       if(a == 0) throw new Exception("zero hated")

       a + b
     })
     val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
     import system.dispatcher
     recoverToSucceededIf[Exception](sumFuture)
     //val sum = Await.result(sumFuture, 2 seconds)
   }
  }

}