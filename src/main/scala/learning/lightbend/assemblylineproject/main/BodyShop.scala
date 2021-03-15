package learning.lightbend.assemblylineproject.main

import akka.stream.scaladsl.Source
import learning.lightbend.assemblylineproject.common.UnfinishedCar

import scala.concurrent.duration.FiniteDuration

class BodyShop(buildTime: FiniteDuration) {
  val cars = Source.tick(buildTime, buildTime, UnfinishedCar())
}
