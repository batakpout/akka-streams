package learning.lightbend.assemblylineproject.tests

import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import learning.lightbend.assemblylineproject.common._
import learning.lightbend.assemblylineproject.main._
import org.scalatest.WordSpecLike

import scala.collection.immutable

class WheelShopTest extends WordSpecLike with AkkaSpec {

  "wheels" should {
    "should return a series of wheels" in {
      val numberToRequest = 100
      val wheelShop = new WheelShop

      val wheels = wheelShop.wheels
        .runWith(TestSink.probe[Wheel])
        .request(numberToRequest)
        .expectNextN(numberToRequest)

      assert(wheels.size === numberToRequest)
      assert(wheels.toSet === Set(Wheel()))
    }
  }

  "installWheels" should {
    "should install four wheels on each car" in {
      val wheelShop = new WheelShop

      val cars = Source.repeat(UnfinishedCar())

      val carsWithWheels: immutable.Seq[UnfinishedCar] = cars.via(wheelShop.installWheels)
        .runWith(TestSink.probe[UnfinishedCar])
        .request(10)
        .expectNextN(10)

      carsWithWheels.foreach { car =>
        assert(car.wheels.size === 4)
      }
    }
  }

}
