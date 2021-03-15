package learning.lightbend.assemblylineproject.main

import akka.NotUsed
import akka.stream.scaladsl.Flow
import learning.lightbend.assemblylineproject.common._
class QualityAssurance {
  val inspect: Flow[UnfinishedCar, Car, NotUsed] = {
    Flow[UnfinishedCar].collect {
      case UnfinishedCar(Some(color), Some(engine), wheels, upgrade) if wheels.size == 4 =>
        Car(serialNumber = SerialNumber(), color = color, engine = engine, wheels = wheels, upgrade = upgrade)
    }
  }
}
