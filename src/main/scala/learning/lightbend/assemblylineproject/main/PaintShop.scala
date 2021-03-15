package learning.lightbend.assemblylineproject.main

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import learning.lightbend.assemblylineproject.common._
class PaintShop(colorSet: Set[Color]) {
  val colors: Source[Color, NotUsed] = Source.cycle(() => colorSet.iterator)

  val paint: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow[UnfinishedCar].zip(colors).map {
      case (car, color) => car.paint(color)
    }
  }
}
