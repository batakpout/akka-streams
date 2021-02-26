package learning.primer.graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.Balance
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.Future

/**
  * Write complex Akka Streams graphs
  * familiarize with the Graph DSL
  * Non-linear components:
  * fan-out components (single input multiple output) : Balance, Broadcast
  * fan-in components (multiple inputs single output): Merge, Zip/ZipWIth, Concat
  */
object Intro1 extends App {
  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  val input: Source[Int, NotUsed] = Source(1 to 1000)
  /**
    * Goal/Requirement is:
    * For each element in Source, two hard computations should be evaluated in parallel,
    * and they should be tupled/paired together
    */

  val incrementer: Flow[Int, Int, NotUsed] = Flow[Int].map[Int](_ + 1) // hard computation 1
  val multiplier: Flow[Int, Int, NotUsed] = Flow[Int].map[Int](_ * 10) // hard computation 2

  /**
    * Now, I want to execute these two flows in parallel somehow, and merge back
    * the result in tuple
    */

  val output: Sink[(Int, Int), Future[Done]] = Sink.foreach[(Int, Int)](println)
  /**
    * So, I like to feed all the numbers from the source to both of above flows,
    * and merge them back into output Sink
    */

  val graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => //mutable Data Structure
      //everything we do below is mutating this builder
      import GraphDSL.Implicits._ //brings some nice operators into scope

      /**
        * Add the necessary components of the Graph
        * */
      //fan-out operator: single input, two outputs
      val broadcast = builder.add(Broadcast[Int](2))
      //fan-in operator
      val zip = builder.add(Zip[Int, Int])

      /**
        * tying up the components
        * */

      //input feeds into broadcast
      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      //finally zips output to sink
      zip.out ~> output
      ClosedShape // must return a shape object, we FREEZE the builder's shape
      //after we return this ClosedShape, builder will be immutable and this ClosedShape
      //will be used to construct the graph.
    } //returns a Graph
  ) // return a runnable Graph


  //graph is a runnable graph
  graph.run()
}

object Intro2 extends App {

  /**
    * Feed a single source into two different sinks at the same time
    */
  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  val input: Source[Int, NotUsed] = Source(1 to 1000)
  val firstSink = Sink.foreach[Int](x => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink: $x"))

  val sourceToTwoSinksGraphs: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      //declaring the components
      val broadcast = builder.add(Broadcast[Int](2))

      //tying up the components
      input ~> broadcast
      broadcast.out(0) ~> firstSink
      broadcast.out(1) ~> secondSink
      ClosedShape
    }

  )
  sourceToTwoSinksGraphs.run()
}

object Intro3 extends App {

  /**
    * Same as Intro2
    */
  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  val input: Source[Int, NotUsed] = Source(1 to 1000)
  val firstSink = Sink.foreach[Int](x => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink: $x"))

  val sourceToTwoSinksGraphs: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      //implicit port numbering, akka streams know which port/out to feed to whom
      // feeds ist one to one first sink, and second one to second sink
      input ~> broadcast ~> firstSink
               broadcast ~> secondSink
      ClosedShape
    }

  )
  sourceToTwoSinksGraphs.run()
}

object Intro4 extends App {

  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  /**
    * Exercise:
    * 1. Two Sources: one fast and one slow source
    * 2. feed into a fan-in component: Merge
    * 3. fan-out using component: balance from Merge to two sinks
    */
    import scala.concurrent.duration._

  val input: Source[Int, NotUsed] = Source(1 to 1000)
  val slowSource = input.throttle(1, 10.second)
  val fastSource = input.throttle(10, 1.second)
  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: $x"))

  val balanceGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() {implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      //declare components
       val merge = builder.add(Merge[Int](2))
       val balance = builder.add(Balance[Int](2))

      /**
        *   fastSource ~> merge.in(0)
            slowSource ~> merge.in(1)
            merge ~> balance
            balance.out(0) ~> sink1
            balance.out(1) ~> sink2
        */
      fastSource ~> merge ~> balance ~> sink1
       slowSource ~> merge
      balance ~> sink2
      //tie them up
      ClosedShape
    }
  )

  /**
    * Regardless of speed of slow/fast source,
    * println come alternatively, Sink 1 then Sink 2 .....
    *
    */
  balanceGraph.run()
}

object Intro5 extends App {

  //extension of Intro4
  implicit val system = ActorSystem("Intro1")
  implicit val materializer = ActorMaterializer()

  import scala.concurrent.duration._

  val input: Source[Int, NotUsed] = Source(1 to 1000)
  val slowSource = input.throttle(2, 4.second)
  val fastSource = input.throttle(6, 1.second)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  val balanceGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() {implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge
      balance ~> sink2
      ClosedShape
    }
  )

  /**
    * Observe, at every single time, the number of elements at Sink1 and Sink2 is equal or very close to each other.
    * So, the number of elements that go through sink1 and sink2 are basically balanced
    * So, that's what this balance fan-out component does
    *
    * So, this balancedGraph does in total, takes two different speed sources emitting elements at different speeds
    * and it even out the rate of production of elements in between these two sources and splits them equally into
    * two sinks
    * Can be a real time example too, different speed source -> end of graph, like to have a study flow of elements.
    * Merge them and then balance them out
    */
  balanceGraph.run()
}