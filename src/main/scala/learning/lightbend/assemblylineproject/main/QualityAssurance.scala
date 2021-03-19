package learning.lightbend.assemblylineproject.main

import akka.NotUsed
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import learning.lightbend.assemblylineproject.common._
import learning.lightbend.assemblylineproject.main.QualityAssurance.CarFailedInspection

object QualityAssurance {

  case class CarFailedInspection(car: UnfinishedCar) extends IllegalStateException(s"Car failed inspection: $car")

}

class QualityAssurance {

  val inspect: Flow[UnfinishedCar, Car, NotUsed] = {

    val decider: Supervision.Decider = {
      case CarFailedInspection(_) => Supervision.Resume
    }

    Flow[UnfinishedCar].map {
      case UnfinishedCar(Some(color), Some(engine), wheels, upgrade) if wheels.size == 4 =>
        Car(SerialNumber(), color, engine, wheels, upgrade)
      case car => throw CarFailedInspection(car)
    }.withAttributes(ActorAttributes.supervisionStrategy(decider))
  }
}
