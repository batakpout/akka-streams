val l = List(1,2,3,4,5)

val r: List[Int] = l.scanLeft(0)(_ + _)

val l = List(1,2,3,4,5)
val x: List[Int] = l.collect({
  case e:Int if e % 2 ==0 => e
})


