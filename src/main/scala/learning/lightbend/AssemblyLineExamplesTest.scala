package learning.lightbend

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import learning.lightbend.assemblylineproject.common._
import learning.lightbend.assemblylineproject.main.PaintShop
import learning.primer.Akka_Stream_Utils

import scala.concurrent.duration.DurationInt

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
  val f = Flow[Color].takeWithin(1.milli)
  val g = colors.via(f).to(sink)
  Akka_Stream_Utils.execute(g)

}

object ShipmentExample extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  import java.util.UUID


  val shipmentSize = 2

  val shipments: Source[Shipment, NotUsed] = {
    Source.fromIterator (() => Iterator.continually {
      Shipment(
        List.fill(shipmentSize)(Engine())
      )
    })
  }

  val sink = Sink.foreach[Shipment](println)

  val g = shipments.takeWithin(1.micro).to(sink)
  //Akka_Stream_Utils.execute(g)

  val engines: Source[Engine, NotUsed] = {
    shipments.mapConcat(shipment => shipment.engines)
  }

 // val k = engines.runWith(Sink.foreach[Engine](println))

  val s2: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())

  val installEngine: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(engines).map {
      case (car, engine) => car.installEngine(engine)
    }
  }

  s2.via(installEngine).takeWithin(1.micro).runWith(Sink.foreach[UnfinishedCar](println))
}

object EmptySourceToFlow extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val paintShop = new PaintShop(Set.empty)
  val colorSet:Set[Color] = Set(Color(11,11,11))
  val colors: Source[Color, NotUsed] = Source.cycle(() => colorSet.iterator)

  val flow: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(colors).map {
      case (car, color) => car.paint(color)
    }.takeWithin(1.microsecond)
  }

  //val s: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())
  val s2: Source[UnfinishedCar, NotUsed] = Source.empty[UnfinishedCar]

  s2.via(flow).to(Sink.foreach[UnfinishedCar](println)).run()

}

object EmptySourceCycleTest extends App {
  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()
  val colorSet = Set()
  val colors: Source[Color, NotUsed] = Source.cycle(() => colorSet.iterator)
  val cars: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())

  val paint: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(colors).map {
      case (car, color) => car.paint(color)
    }
  }.recover {
    case e: Throwable =>
      println(":Error message==>" + e.getMessage)
      UnfinishedCar()
  }

  cars.via(paint).to(Sink.foreach[UnfinishedCar](println)).run()
}

object WheelTest extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val cars: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())

  val wheels: Source[Wheel, NotUsed] = {
    Source.repeat(Wheel())
  }
  def installWheels: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(wheels.grouped(4)).map {
      case (car, wheels) => car.installWheels(wheels)
    }
  }

  cars.via(installWheels).to(Sink.foreach[UnfinishedCar](println)).run()
}

object WheelLoopTest extends App {

  implicit val system = ActorSystem("FirstPrinciples-9")
  implicit val materializer = ActorMaterializer()

  val cars: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())

  val wheels: Source[Wheel, NotUsed] = {
    Source.repeat(Wheel())
  }

  def installWheels: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(wheels.grouped(4)).map {
      case (car, wheels) => {
        println("Inside=====")
        car.installWheels(wheels)
      }
    }
  }


  cars.via(installWheels).take(4).runWith(Sink.ignore)
}