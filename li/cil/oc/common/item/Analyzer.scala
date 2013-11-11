package li.cil.oc.common.item

import cpw.mods.fml.common.network.Player
import li.cil.oc.Config
import li.cil.oc.api.network.Environment
import li.cil.oc.common.tileentity
import li.cil.oc.server.PacketSender
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class Analyzer(val parent: Delegator) extends Delegate {
  val unlocalizedName = "Analyzer"

  override def onItemUse(item: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float) = {
    world.getBlockTileEntity(x, y, z) match {
      case computer: tileentity.Computer =>
        if (!world.isRemote) {
          computer.instance.lastError match {
            case Some(value) => player.addChatMessage("Last error: " + value)
            case _ =>
          }
          processAddress(computer, player)
        }
        true
      case screen: tileentity.Screen =>
        if (!world.isRemote) {
          processAddress(screen.origin, player)
        }
        true
      case environment: Environment =>
        if (!world.isRemote) {
          processAddress(environment, player)
        }
        true
      case _ => super.onItemUse(item, player, world, x, y, z, side, hitX, hitY, hitZ)
    }
  }

  private def processAddress(environment: Environment, player: EntityPlayer) {
    val address = environment.node.address()
    player.addChatMessage("Address: " + address)
    if (player.isSneaking) {
      PacketSender.sendClipboard(address, player.asInstanceOf[Player])
    }
  }

  override def registerIcons(iconRegister: IconRegister) {
    super.registerIcons(iconRegister)

    icon = iconRegister.registerIcon(Config.resourceDomain + ":analyzer")
  }
}