package learning.lightbend.assemblylineproject.main

import akka.NotUsed
import akka.stream.scaladsl._
import learning.lightbend.assemblylineproject.common._
class WheelShop {
  val wheels: Source[Wheel, NotUsed] = {
    Source.repeat(Wheel())
  }

  def installWheels: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(wheels.grouped(4)).map {
      case (car, wheels) => car.installWheels(wheels)
    }
  }
}






