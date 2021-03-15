package learning.lightbend.assemblylineproject.common

sealed trait Upgrade

object Upgrade {
  case object DX extends Upgrade
  case object Sport extends Upgrade
}
