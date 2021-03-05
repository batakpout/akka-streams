package learning.primer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.{ActorMaterializer, IOResult, OverflowStrategy}
import akka.stream.scaladsl.{FileIO, Flow, Keep, RunnableGraph, Sink, Source, StreamConverters, Tcp}
import akka.util.ByteString
import akka.{Done, NotUsed}

import java.io.{FileInputStream, FileOutputStream}
import java.nio.file.Paths
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object First_Principles_1 extends App {

  implicit val system = ActorSystem("FirstPrinciples-1")
  implicit val materializer = ActorMaterializer()

  //from iterable
  // the stream is completed when there is no data in the iterable
  val iterable: immutable.Seq[Int] = (1 to 10)
  //sources
  val source: Source[Int, NotUsed] = Source(1 to 10)
  //sink
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
  val res: NotUsed = graph.run


}

object First_Principles_2 extends App {

  implicit val system = ActorSystem("FirstPrinciples-2")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
  val res: NotUsed = graph.run

}

object First_Principles_3 extends App {

  implicit val system = ActorSystem("FirstPrinciples-3")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)

  val graph: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)
  val res: Future[Int] = graph.run

  import scala.concurrent.Await
  import scala.concurrent.duration._

  println(Await.result(res, 2.second))
}

object First_Principles_4 extends App {

  implicit val system = ActorSystem("FirstPrinciples-4")
  implicit val materializer = ActorMaterializer()

  //flow transforms elements

  val source: Source[Int, NotUsed] = Source[Int](1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 2) //mentioning type is necessary

  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  val flowToSink: Sink[Int, NotUsed] = flow.to(sink) //toMat(sink)(Keep.left) //keeping materialized value of left i.e flow i.e Not Used

  val typ1: RunnableGraph[NotUsed] = sourceWithFlow.to(sink)
  val typ2: RunnableGraph[NotUsed] = source.to(flowToSink)

  val rG: RunnableGraph[NotUsed] = source.via(flow).to(sink)
  val result: NotUsed = rG.run()
}

object First_Principles_5 extends App {

  implicit val system = ActorSystem("FirstPrinciples-5")
  implicit val materializer = ActorMaterializer()
  /** Exception in thread "main" java.lang.NullPointerException: Element must not be null, rule 2.13 */
  //null values are not allowed, use Option instead
  val illegalSource = Source.single[String](null)
  illegalSource.to(Sink.foreach(println)).run()
}

object First_Principles_6 extends App {

  implicit val system = ActorSystem("FirstPrinciples-6")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //various kinds of sources

  val finiteSource: Source[Int, NotUsed] = Source.single(1)
  val anotherFiniteSource: Source[Int, NotUsed] = Source(List(1, 2, 3, 4))
  val emptySource: Source[Int, NotUsed] = Source.empty[Int]

  Akka_Stream_Utils.execute(finiteSource.to(sink))

  Thread.sleep(1000)
  println("---1----")

  Akka_Stream_Utils.execute(anotherFiniteSource.to(sink))

  Thread.sleep(1000)
  println("---2----")

  Akka_Stream_Utils.execute(emptySource.to(sink))
  println("--3----")
  val infiniteStream: Source[Int, NotUsed] = Source(Stream.from(1)) // do not confuse an akka stream with a "collection" Stream
  //Akka_Stream_Utils.execute(infiniteStream.to(sink))

  Thread.sleep(1000)
  println("--4----")

  import system.dispatcher

  val futureSource: Source[Int, NotUsed] = Source.fromFuture(Future(42))
  val res: RunnableGraph[NotUsed] = futureSource.to(sink)
  Akka_Stream_Utils.execute(res)

  Thread.sleep(1000)
  println("--5----")

}

object First_Principles_7 extends App {

  implicit val system = ActorSystem("FirstPrinciples-7")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)

  val theMostBoringSink: Sink[Any, Future[Done]] = Sink.ignore

  Akka_Stream_Utils.execute(source.to(theMostBoringSink))

  Thread.sleep(1000)
  println("----1----")

  val forEachSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  Akka_Stream_Utils.execute(source.to(forEachSink))

  Thread.sleep(1000)
  println("----2----")

  val headSink: Sink[Int, Future[Int]] = Sink.head[Int] //retrieves head and then closes the stream
  Akka_Stream_Utils.executeWithPrintln(source.toMat(headSink)(Keep.right))

  Thread.sleep(1000)
  println("----3----")

  val foldSink = Sink.fold[Int, Int](0)(_ + _)
  Akka_Stream_Utils.executeWithPrintln(source.toMat(foldSink)(Keep.right))

  Thread.sleep(1000)
  println("----4----")


}

object First_Principles_8 extends App {

  implicit val system = ActorSystem("FirstPrinciples-8")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //Flows, usually mapped to collection operators
  val mapFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 2)
  val takeFlow: Flow[Int, Int, NotUsed] = Flow[Int].take(2)


  //same way drop, filer, NOT have flatMap

  Akka_Stream_Utils.execute(source.via(mapFlow).to(sink))

  Thread.sleep(1000)
  println("----1----")

  Akka_Stream_Utils.execute(source.via(takeFlow).to(sink))

  Thread.sleep(1000)
  println("----2----")

  Akka_Stream_Utils.execute(source.via(mapFlow).via(takeFlow).to(sink))
}

object First_Principles_9 extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 4)
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //some syntactic sugar

  val mapSource: Source[Int, NotUsed] = Source(1 to 4).map(_ + 2) // equivalent to Source(1 to 10).via(Flow[Int].map(_ + 2))

  Akka_Stream_Utils.execute(mapSource.to(sink))

  Thread.sleep(1000)
  println("----1----")

  //run Stream directly
  val result: Future[Done] = mapSource.runWith(sink) //equivalent to mapSource.to(Sink.foreach(println)).run()

  Thread.sleep(1000)
  println("----2----")

  val result2: Future[Done] = mapSource.runForeach(println) // equivalent to mapSource.to(Sink.foreach[Int](println))

  Thread.sleep(1000)
  println("----2----")

  // OPERATORS = components

  val names = List("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams")
  val nameSource = Source(names)
  val longNameFlow = Flow[String].filter(name => name.length > 5)
  val limitFlow = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)

  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()

  Thread.sleep(1000)
  println("----3----")

  //same as above
  nameSource.filter(_.length > 5).take(2).runForeach(println)

}

object First_Principles_10 extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val source: Source[String, NotUsed] = Source.repeat("Hello World!")
  /**
    * Same element is infinitely pushed whenever there is demand
    */
  val sink: Sink[String, Future[Done]] = Sink.foreach[String](println)

  val g = source.to(sink)
  Akka_Stream_Utils.execute(g)


}

object First_Principles_11 extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  import scala.concurrent.duration._

  val source: Source[String, Cancellable] = Source.tick(
    initialDelay = 2.seconds,
    interval = 1.seconds,
    "Hello World!")
  /**
    * Same element is infinitely pushed whenever there is demand
    */
  val sink: Sink[String, Future[Done]] = Sink.foreach[String](println)

  val g: RunnableGraph[Cancellable] = source.to(sink)
  Akka_Stream_Utils.execute(g)

}

object First_Principles_12 extends App {

  // from iterators
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  /**
    * Create a Source from Iterator
    * The Iterator is created each time the Source is materialized
    * Push elements from the iterator whenever there is demand.
    * Completes when hasNext returns false
    */
  val source: Source[Int, NotUsed] = Source.fromIterator(
    () => Iterator.from(1)
    //() => Iterator.range(0, 10)
  )
  /**
    * Same element is infinitely pushed whenever there is demand
    */
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val g = source.to(sink)
  Akka_Stream_Utils.execute(g)

}

object First_Principles_13 extends App {

  // from iterators
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()


  /**
    * Similar to Iterator.from, but the iterator in this case is infinitely repeated.
    * When hasNext returns false, the iterator is recreated and consumed again.
    */
  val source: Source[Int, NotUsed] = Source.cycle(
    () => Iterator.range(1, 5)
  )

  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val g = source.to(sink)
  Akka_Stream_Utils.execute(g)

}

object First_Principles_14 extends App {

  // from iterators
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()


  /**
     * unfold
     * uses an initial value and a transformation function.
     * The transformation function returns an Option of a tuple containing
        the value for the next iteration, and the value to push.
     * completes when the transformation function returns None.
     * unfoldAsync, same as unfold, but function return Future of an Option
    */
  val source: Source[Int, NotUsed] = Source.unfold(0) {
    case value if value <= 20 => Some((value + 1, value))
    case _ => None
  }

  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val g = source.to(sink)
  Akka_Stream_Utils.execute(g)

  Thread.sleep(5000)
  println("===================")

  val fiboSource: Source[Int, NotUsed] = Source.unfold(0 -> 1) {
    case (a, _) if a > 50 ⇒ None
    case (a, b) ⇒ Some((b -> (a + b)) -> a)
  }

  val g2 = fiboSource.to(sink)
  Akka_Stream_Utils.execute(g2)

}

object SourceFromActors extends App {

  /**
    * Creates a source that is materialized as an ActorRef.
    * Messages send to the ActorRef will be pushed to the stream or buffered until there is demand.
    * Completes by sending the actor a akka.actor.Status.Success or akka.actor.PoisonPill
    */
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val materializedActorRef: ActorRef = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()
  materializedActorRef ! 10
  // terminating the stream
  materializedActorRef ! akka.actor.Status.Success("complete")
  materializedActorRef ! 10
}

object SourceFromFiles extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  /**
    * Creates s Source of ByteString from a file using a thread-pool backed dispatcher dedicated for FileIO
    * Pulls data from the file and pushes it downstream whenever there is demand.
    * Completes when the end of file is reached
    * Use the Framing API and a decoder to parse ByteStrings into lines of text
    */

  val byteSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(
    Paths.get("src/main/resources/logfile.txt"),
    chunkSize = 1024
  )
}

object SourceFromTCPConnections extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

 /**
   * Tcp().bind()
   * Creates a Source from TCP Connection
   * Each time a client connects, a new Connection will be emitted to the stream.
   * Connection can be processed by attaching a Flow to the Connection.

  */
  val connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp().bind("127.0.0.1", 8888)
}

object SourceFromJavaStreams extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

 val source: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream { () =>
   new FileInputStream("myfile.txt")
 }
}

object FlowFromSinkAndShapeTest extends App {

  implicit val system = ActorSystem("FlowFromSinkAndShapeTest")
  implicit val materializer = ActorMaterializer()

  // close in immediately
  val sink = Sink.cancelled[ByteString]
  // periodic tick out
  val source =
    Source.tick(1.second, 1.second, "tick").map(_ => ByteString("Hello Telnet" + "\n"))

  val serverFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(sink, source)

  val r: Future[Done] = Tcp().bind("127.0.0.1", 9999).runForeach { incomingConnection: Tcp.IncomingConnection =>
    incomingConnection.handleWith(serverFlow)
  }
}

object TickSource extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  import scala.concurrent.duration._

  val source: Source[String, Cancellable] = Source.tick(1.second, 1.second, "tick").map(_ => "Hello Ticky")

  source.runWith(Sink.foreach(println))
}




object ColorIterator extends App {

  object Color {
    def apply(hexString: String): Color = {
      new Color(
        Integer.parseInt(hexString.substring(0, 2), 16),
        Integer.parseInt(hexString.substring(2, 4), 16),
        Integer.parseInt(hexString.substring(4, 6), 16)
      )
    }
  }
  case class Color(red: Int, green: Int, blue: Int) {
    require(red >= 0 && red <= 255)
    require(green >= 0 && green <= 255)
    require(blue >= 0 && blue <= 255)
  }

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val colorSet = Set(
    Color("FFFFFF"),
    Color("000000"),
    Color("FF00FF")
  )
  val colors: Source[Color, NotUsed] = Source.cycle(() => colorSet.iterator)



  val sink = Sink.foreach[Color](println)

  val g = colors.to(sink)
  Akka_Stream_Utils.execute(g)

}

object ShipmentExample extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  import java.util.UUID

  case class SerialNumber(value: UUID = UUID.randomUUID())
  case class Engine(serialNumber: SerialNumber = SerialNumber())
  case class Shipment(engines: Seq[Engine])

  val shipmentSize = 2

  val shipments: Source[Shipment, NotUsed] = {
    Source.fromIterator (() => Iterator.continually {
      Shipment(
        Seq.fill(shipmentSize)(Engine())
      )
    })
  }

  val sink = Sink.foreach[Shipment](println)

  val g = shipments.to(sink)
  Akka_Stream_Utils.execute(g)
}


object FlowFromSinkAndSource extends App {


  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  import scala.concurrent.duration._

  val sink: Sink[String, NotUsed] = Sink.cancelled[String]
  // periodic tick out
  val source =
    Source.tick(1.second, 1.second, "tick").map(_ => "Hello Telnet")

  val serverFlow: Flow[String, String, NotUsed] = Flow.fromSinkAndSource(sink, source)

  val f1 = Flow[String].map(_ + "a")
  val x: NotUsed = f1.joinMat(serverFlow)(Keep.right).run()


}

object SinkSeq extends App {

  /**
    * Sink.seq[Int]
    * Pulls all elements in the stream and populates a sequence: Vector()
    */
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val source = Source( 1 to 10)
  val sink: Sink[Int, Future[immutable.Seq[Int]]] = Sink.seq[Int]

  val res: Future[immutable.Seq[Int]] = source.runWith(sink)

  import system.dispatcher

  res.map(x => println(s"Sequesnce is: ${x}"))

}


object SinkActorRef extends App {

  /**
    * Pulls all elements in the stream and sends them to a provided ActorRef.
    * when the stream completes, it will send the onCompleteMessage to the actor.
    * No Backpressure mechanism is provided. Beware of mailbox overflow.
    * actorRefAck: Sends the elements of the stream to the given ActorRef that sends back back-pressure signal
    */
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val source = Source( 1 to 10)

  case object PrintSum
  val sumActor: ActorRef = system.actorOf(Props(
    new Actor with ActorLogging {
      private var sum = 0

      override def receive: Receive = {
        case value: Int => sum += value
        case PrintSum => log.info(s"sum is: $sum")
      }
    }
  ))

  val computeSum: Sink[Int, NotUsed] = Sink.actorRef[Int](sumActor, onCompleteMessage = PrintSum)


}

object SinksToFiles extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val writeToFile: Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(Paths.get("myfile.txt"))

  //FileIO.toPath : pulls ByteStrings from upstream and write them to a file.
}

object SinkToJavaStream extends App {

  /**
    * Pulls data from upstream and writes them to the OuputStream.
    */
  val writeToJavaStream: Sink[ByteString, Future[IOResult]] =
    StreamConverters.fromOutputStream(() =>
        new FileOutputStream("file.txt")
    )
}



