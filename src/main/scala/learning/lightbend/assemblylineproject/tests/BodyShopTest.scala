package learning.lightbend.assemblylineproject.tests

import akka.stream.scaladsl.Sink
import learning.lightbend.assemblylineproject.main._
import org.scalatest.WordSpecLike

import scala.concurrent.duration._
class BodyShopTest extends WordSpecLike with AkkaSpec {
  "cars" should  {
    "should return cars at the expected rate" in {
      val bodyShop = new BodyShop(buildTime = 200.millis)

      val cars = bodyShop.cars
        .takeWithin(1000.millis)
        .runWith(Sink.seq)
        .futureValue

       println(s"Retrieved cars: ${cars.size}")
      assert(cars.size == 5)
    }
  }
}
