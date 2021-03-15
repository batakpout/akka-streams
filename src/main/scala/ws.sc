case class UP(model: String)

case class Mod(mod: Option[UP])

val l: List[Mod] = List(Mod(Some(UP(""))), Mod(None), Mod(None))

val x: List[Option[UP]] = l.map(_.mod)

x.count(_.isEmpty)
x.count(_.nonEmpty)


