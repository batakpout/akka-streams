package learning.primer

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.RunnableGraph

object Akka_Stream_Utils {

  def execute[T](rG: RunnableGraph[T])(implicit actorMaterializer: ActorMaterializer) = {
    rG.run()
  }

  def executeWithPrintln[T](rG: RunnableGraph[T])(implicit actorMaterializer: ActorMaterializer) = {
    println {
      rG.run()
    }
  }

}