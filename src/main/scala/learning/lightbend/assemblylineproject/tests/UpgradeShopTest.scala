package learning.lightbend.assemblylineproject.tests


import akka.stream.scaladsl.{Sink, Source}
import learning.lightbend.assemblylineproject.common._
import learning.lightbend.assemblylineproject.main._
import org.scalatest.WordSpecLike

import scala.collection.immutable

class UpgradeShopTest extends WordSpecLike with AkkaSpec {

  "upgrade" should {
    "should upgrade the correct ratio of cars" in {
      val numCars = 12
      val upgradeShop = new UpgradeShop

      val cars = Source(1 to numCars)
        .map(_ => UnfinishedCar())
        .via(upgradeShop.installUpgrades)
        .runWith(Sink.seq)
        .futureValue

      val upgrades: immutable.Seq[Option[Upgrade]] = cars.map(_.upgrade)

      assert(upgrades.count(_.isEmpty) === numCars/3)
      assert(upgrades.count(_.contains(Upgrade.DX)) === numCars/3)
      assert(upgrades.count(_.contains(Upgrade.Sport)) === numCars/3)
    }
  }
}
