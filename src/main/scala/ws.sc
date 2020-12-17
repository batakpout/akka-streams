import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val o1: Option[None.type] = Some(11).map(x => None)
val o2: Option[Int] = Some(11).flatMap(x => Some(12))
val o3:Option[None.type]  = Some(11).flatMap(x => None)

val l  = List(Some(12), None, Some(90))
l.flatMap(x => x)


val l = List(List(12), Nil, List(90)).flatMap(x => x)

case object Hello

val x: Hello.type = Hello

/*class List[+A] {
  def flatMap[B](f: A => List[B]):List[B] = {
    if(isEmpty) Nil else {
      f(head) concat tail.flatMap(f))
    }
  }
}*/

val xzz: Option[Nothing] = None
val fruits = Seq("apple", "banana", "orange")
fruits.map(_.toUpperCase)

List("APPLE") ::: List("BANANA") ::: List("ORANGE")

