package learning.lightbend.assemblylineproject.main


import akka.stream.scaladsl.Sink
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import learning.lightbend.assemblylineproject.common.{Car, Color}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Factory(bodyShop: BodyShop,
              paintShop: PaintShop,
              engineShop: EngineShop,
              wheelShop: WheelShop,
              qualityAssurance: QualityAssurance)
             (implicit system: ActorSystem, materializer: ActorMaterializer) {
  def orderCars(quantity: Int): Future[Seq[Car]] = {
    bodyShop.cars
      .via(paintShop.paint)
      .via(engineShop.installEngine)
      .via(wheelShop.installWheels)
      .via(qualityAssurance.inspect)
      .take(quantity)
      .runWith(Sink.seq)
  }
}

object FactoryTest extends App {

  implicit val system = ActorSystem("factorytest")
  implicit val materializer = ActorMaterializer()

  val color = Color("000000")
  val bodyShop = new BodyShop(buildTime = 1.milli)
  val paintShop = new PaintShop(Set(color))
  val engineShop = new EngineShop(shipmentSize = 20)
  val wheelShop = new WheelShop()
  val qualityAssurance = new QualityAssurance()
  val factory = new Factory(bodyShop, paintShop, engineShop, wheelShop, qualityAssurance)

  import system.dispatcher
  factory.orderCars(2).map {x =>
    println(x)
    println("\n\n")
  }


}