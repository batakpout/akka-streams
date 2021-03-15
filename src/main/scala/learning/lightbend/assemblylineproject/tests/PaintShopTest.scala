package learning.lightbend.assemblylineproject.tests

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import learning.lightbend.assemblylineproject.main._
import org.scalatest.WordSpecLike
import learning.lightbend.assemblylineproject.common._

import scala.collection.immutable.Seq

class PaintShopTest extends WordSpecLike with AkkaSpec {

  "colors" should {
    "should repeat each color in the color set" in {
      val colorSet: Set[Color] = Set(
        Color("FFFFFF"),
        Color("000000"),
        Color("FF00FF")
      )

      val paintShop = new PaintShop(colorSet)

      val colors = paintShop.colors
        .runWith(TestSink.probe[Color])
        .request(colorSet.size * 2)
        .expectNextN(colorSet.size * 2)

      assert(colors === colorSet.toSeq ++ colorSet.toSeq)
    }
  }

  "paint" should {
    "should throw an error if there are no colors" in {
      val paintShop = new PaintShop(Set.empty) //emptySet.iterator throws error
      val cars: Source[UnfinishedCar, NotUsed] = Source.repeat(UnfinishedCar())

      cars.via(paintShop.paint)
        .runWith(TestSink.probe[UnfinishedCar])
        .request(10)
        //.expectComplete()
        //.expectNextN(12) to fail it
        .expectError()
    }
    "should terminate if there are no cars" in {
      val paintShop = new PaintShop(Set(Color("000000")))
      val cars = Source.empty[UnfinishedCar]

      cars.via(paintShop.paint)
        .runWith(TestSink.probe[UnfinishedCar])
        .request(10)
        //.expectNextN(10)
        .expectComplete()
    }
    "should apply the paint colors to the cars" in {
      val color = Color("000000")
      val car = UnfinishedCar()
      val paintShop = new PaintShop(Set(color))
      val cars = Source.repeat(car)

      cars.via(paintShop.paint)
        .runWith(TestSink.probe[UnfinishedCar])
        .request(10)
        .expectNextN(Seq.fill(10)(car.copy(color = Some(color))))
    }
  }

}
