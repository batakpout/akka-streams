package learning.advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable
import scala.util.Random

/**
  * -> Custom operator with Graph Stages.
  * -> Create our own components with custom logic.
  *
  */
object Custom_Operators_1 extends App {

  implicit val system = ActorSystem("CO-1")
  implicit val materializer = ActorMaterializer()

  //  a custom source which emits random numbers until canceled
  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // step 4:
      // define mutable state
      // implement my logic here

      //setHandler --> Assigns callbacks for the events for an Outlet/InLet
      //OutHandler/InHandler --> Collection of callbacks for an output/input port of a GraphStage
      setHandler(outPort, new OutHandler {
        //in OutHandler there is no DownStreamFailure callback, as it will receive a cancel signal
        override def onPull(): Unit = {
          val nextNumber = random.nextInt(max)
          //emit a new element when there is demand from downstream, push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }

  }


  // - a custom sink that prints elements in batches of a given size
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape(inPort)

    //mutable state
    val batch = new mutable.Queue[Int]

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
         //be the first to ask source for an element
        pull(inPort)
      }

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          Thread.sleep(100) //assume some complex computation here, we see backpressure applied automatically.

          if (batch.size >= batchSize) {
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
          }
          pull(inPort) // signal more demand
        }

        override def onUpstreamFinish(): Unit = {
          //what if the stream terminates abruptly, quite likely there are still some elements in the queue, flash out those elements
          if (batch.nonEmpty) {
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
            println("Stream finished....")
          }
        }
      })
    }

  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  val batcherSink = Sink.fromGraph(new Batcher(10))

  randomGeneratorSource.runWith(batcherSink)
  /**
    * It won't print anything, voo, reason
     --> onPull waits for demand from downstream, and onPush wait upstream to send an element,
        so both wait for each other [chicken and egg problem], each component is waiting for the other to give the
        signal, but no one is sending it, so no output.
    --> We will make sink- the batcher to send the first signal in preStart method inside GraphStageLogic remember.
    */

  /**
    * Also Sink - the batcher by itself will apply backpressure i.e the backpressure aspect of these components
      will still happen automatically.
    we can prove this by putting Thread.sleep in batcher.
    i.e Slow Consumer back pressuring a fast producer automatically.
    */
}