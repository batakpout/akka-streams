package learning.advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Graph, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}


object CustomGraphWithFlows_1 extends App {

  implicit val system = ActorSystem("CO-1")
  implicit val materializer = ActorMaterializer()

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          val nextNumber = random.nextInt(max)
          push(outPort, nextNumber)
        }
      })
    }

  }

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape(inPort)

    val batch = new mutable.Queue[Int]

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)
      }

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          Thread.sleep(100)

          if (batch.size >= batchSize) {
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
          }
          pull(inPort)
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
            println("Stream finished....")
          }
        }
      })
    }

  }

  class SimpleFlowFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

    val inPort = Inlet[T]("filterIn")
    val outPort = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      setHandler(outPort, new OutHandler {
        //If downstream ask me for an element on outPort, I will just ask my upstream for element on inPort
        override def onPull(): Unit = pull(inPort)
      })

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          val nextElement = grab(inPort)

          if (predicate(nextElement)) {
            push(outPort, nextElement)
          } else pull(inPort)
        }
      })
    }

  }

  val flowGraph: Graph[FlowShape[Int, Int], NotUsed] = new SimpleFlowFilter[Int](_ > 50)
  val flow: Flow[Int, Int, NotUsed] = Flow.fromGraph(flowGraph)

  val randomGeneratorSource: Source[Int, NotUsed] = Source.fromGraph(new RandomNumberGenerator(100))
  val batcherSink: Sink[Int, NotUsed] = Sink.fromGraph(new Batcher(10))

  //randomGeneratorSource.via(flow).to(batcherSink).run

  //a flow that counts the number of elements that go through it
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort = Inlet[T]("counterInt")
    val outPort = Outlet[T]("counterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]()
      val graphStageLogic = new GraphStageLogic(shape) {

        var counter = 0

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            val nextElement = grab(inPort)
            counter += 1
            push(outPort, nextElement)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        }

        )
      }
      (graphStageLogic, promise.future)
    }

  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val countFuture: Future[Int] = Source(1 to 10)
    //.map(x => if (x == 7) throw new RuntimeException("gotcha!") else x)
    .viaMat(counterFlow)(Keep.right)
    //.to(Sink.foreach(println)).run()
    .to(Sink.foreach(x => if (x == 7) throw new RuntimeException("gotcha, sink!") else println(x))).run()
  //when sink throws error means, it completes itself


  import system.dispatcher

  countFuture.onComplete {
    case Success(count) => println(s"The number of elements passed: $count")
    case Failure(ex) => println(s"Counting the elements failed: $ex")
  }

  /**
    * Handler callbacks: onPull, onPush are never called concurrently so we can access safely
    * mutable state inside of them.
    * We should never expose these mutable state outside of these handlers e.g on future complete callbacks.
    * This could break the component encapsulation just like actor encapsulation
    */

}