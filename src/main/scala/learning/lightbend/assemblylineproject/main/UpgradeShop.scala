package learning.lightbend.assemblylineproject.main


import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge}
import learning.lightbend.assemblylineproject.common._

class UpgradeShop {

  val installUpgrades: Flow[UnfinishedCar, UnfinishedCar, NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val balance = builder.add(Balance[UnfinishedCar](3))
      val merge = builder.add(Merge[UnfinishedCar](3))
      val upgradeToDX = Flow[UnfinishedCar].map(car => car.installUpgrade(Upgrade.DX))
      val upgradeToSport = Flow[UnfinishedCar].map(car => car.installUpgrade(Upgrade.Sport))

      balance ~> upgradeToDX    ~> merge
      balance ~> upgradeToSport ~> merge
      balance                   ~> merge

      FlowShape(balance.in, merge.out)
    })
  }
}
