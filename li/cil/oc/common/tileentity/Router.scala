package li.cil.oc.common.tileentity

import cpw.mods.fml.relauncher.{Side, SideOnly}
import li.cil.oc.api.network.{Node, Message, Visibility}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.{Blocks, Settings, api}
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection

class Router extends net.minecraft.tileentity.TileEntity with api.network.SidedEnvironment {
  private val plugs = ForgeDirection.VALID_DIRECTIONS.map(side => new Plug(side))

  // ----------------------------------------------------------------------- //

  @SideOnly(Side.CLIENT)
  def canConnect(side: ForgeDirection) = true

  def sidedNode(side: ForgeDirection) = plugs(side.ordinal()).node

  // ----------------------------------------------------------------------- //

  override def canUpdate = false

  override def validate() {
    super.validate()
    worldObj.scheduleBlockUpdateFromLoad(xCoord, yCoord, zCoord, Blocks.router.parent.blockID, Int.MinValue, 0)
  }

  override def invalidate() {
    super.invalidate()
    for (plug <- plugs if plug.node != null) {
      plug.node.remove()
    }
  }

  override def onChunkUnload() {
    super.onChunkUnload()
    for (plug <- plugs if plug.node != null) {
      plug.node.remove()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBT(nbt: NBTTagCompound) {
    super.readFromNBT(nbt)
    nbt.getTagList(Settings.namespace + "plugs").iterator[NBTTagCompound].zip(plugs).foreach {
      case (plugNbt, plug) => plug.node.load(plugNbt)
    }
  }

  override def writeToNBT(nbt: NBTTagCompound) {
    super.writeToNBT(nbt)
    nbt.setNewTagList(Settings.namespace + "plugs", plugs.map(plug => {
      val plugNbt = new NBTTagCompound()
      plug.node.save(plugNbt)
      plugNbt
    }))
  }

  // ----------------------------------------------------------------------- //

  private class Plug(val side: ForgeDirection) extends api.network.Environment {
    val node = api.Network.newNode(this, Visibility.Network).create()

    def onMessage(message: Message) {
      if (isPrimary && message.name == "network.message") {
        plugsInOtherNetworks.foreach(_.node.sendToReachable(message.name, message.data: _*))
      }
    }

    def onDisconnect(node: Node) {}

    def onConnect(node: Node) {}

    private def isPrimary = plugs(plugs.indexWhere(_.node.network == node.network)) == this

    private def plugsInOtherNetworks = plugs.filter(_.node.network != node.network)
  }

}
