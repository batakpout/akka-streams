package learning.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, FlowShape, Graph, SinkShape, SourceShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

/**
  * Example: Cryptography, Encoding/Decoding, Serializing/DeSerializing
  * A bidirectional flow of elements that consequently has two inputs and two outputs, arranged like this:
  *
  *           +------+
  *   In1 ~>  |       |~> Out1
  *           |  bidi |
  *     Out2<~|       |<~ In2
  *            +------+
  */

object BiDirectionalFlows_1 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def encrypt(n: Int)(s: String) = s.map(c => (c + n).toChar)

  def decrypt(n: Int)(s: String) = s.map(c => (c - n).toChar)

  /**
    * They encrypt, decrypt often stay in the same place, i.e stay in the same component: bidiFlow
    */

  val bidiCryptoStaticGraph: Graph[BidiShape[String, String, String, String], NotUsed] = GraphDSL.create() { implicit builder =>
    val encryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(decrypt(3)))

    BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    //BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings: List[String] = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  val unencryptedSource: Source[String, NotUsed] = Source(unencryptedStrings)
  val encryptedSource: Source[String, NotUsed] = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape: SourceShape[String] = builder.add(unencryptedSource)
      val encryptedSourceShape: SourceShape[String] = builder.add(encryptedSource)
      val bidi: BidiShape[String, String, String, String] = builder.add(bidiCryptoStaticGraph)

      val encryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](str => println(s"Encrypted: $str")))
      val decryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](str => println(s"Decrypted: $str")))

      unencryptedSourceShape ~> bidi.in1
      bidi.out1 ~> encryptedSinkShape
      encryptedSourceShape ~> bidi.in2
      bidi.out2 ~> decryptedSinkShape
      ClosedShape
    }
  )

  val cryptoBidiGraph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape: SourceShape[String] = builder.add(unencryptedSource)
      val encryptedSourceShape: SourceShape[String] = builder.add(encryptedSource)
      val bidi: BidiShape[String, String, String, String] = builder.add(bidiCryptoStaticGraph)

      val encryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](str => println(s"Encrypted: $str")))
      val decryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](str => println(s"Decrypted: $str")))

      //revers the way these components are tied in
      unencryptedSourceShape ~> bidi.in1 ; bidi.out1 ~> encryptedSinkShape
      bidi.in2 <~ encryptedSourceShape ; decryptedSinkShape <~ bidi.


      ClosedShape
    }
  )

  cryptoBidiGraph1.run()

  Thread.sleep(5000)
  println("=" * 40)
  cryptoBidiGraph2.run()

}