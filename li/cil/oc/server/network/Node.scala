package li.cil.oc.server.network

import li.cil.oc.api
import li.cil.oc.api.network.{Environment, Visibility, Node => ImmutableNode}
import net.minecraft.nbt.NBTTagCompound
import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._

class Node(val host: Environment, val name: String, val reachability: Visibility) extends api.network.Node {
  final var address: String = null

  final var network: api.network.Network = null

  def canBeReachedFrom(other: ImmutableNode) = reachability match {
    case Visibility.None => false
    case Visibility.Neighbors => isNeighborOf(other)
    case Visibility.Network => isInSameNetwork(other)
  }

  def isNeighborOf(other: ImmutableNode) =
    isInSameNetwork(other) && network.neighbors(this).exists(_ == other)

  def reachableNodes: java.lang.Iterable[ImmutableNode] =
    if (network == null) Iterable.empty[ImmutableNode].toSeq
    else network.nodes(this)

  def neighbors: java.lang.Iterable[ImmutableNode] =
    if (network == null) Iterable.empty[ImmutableNode].toSeq
    else network.neighbors(this)

  def connect(node: ImmutableNode) = network.connect(this, node)

  def disconnect(node: ImmutableNode) =
    if (network != null) network.disconnect(this, node)

  def remove() = if (network != null) network.remove(this)

  def sendToAddress(target: String, name: String, data: AnyRef*) =
    if (network != null) network.sendToAddress(this, target, name, data: _*)

  def sendToNeighbors(name: String, data: AnyRef*) =
    if (network != null) network.sendToNeighbors(this, name, data: _*)

  def sendToReachable(name: String, data: AnyRef*) =
    if (network != null) network.sendToReachable(this, name, data: _*)

  private def isInSameNetwork(other: ImmutableNode) =
    network != null && network == other.network

  // ----------------------------------------------------------------------- //

  def load(nbt: NBTTagCompound) = {
    if (nbt.hasKey("oc.node.address"))
      address = nbt.getString("oc.node.address")
  }

  def save(nbt: NBTTagCompound) = {
    if (address != null)
      nbt.setString("oc.node.address", address)
  }
}