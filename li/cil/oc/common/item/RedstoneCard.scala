package li.cil.oc.common.item

import cpw.mods.fml.common.Loader
import java.util
import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack

class RedstoneCard(val parent: Delegator) extends Delegate {
  val unlocalizedName = "RedstoneCard"

  override def tooltipLines(stack: ItemStack, player: EntityPlayer, tooltip: util.List[String], advanced: Boolean) {
    tooltip.addAll(Tooltip.get(unlocalizedName))
    if (Loader.isModLoaded("RedLogic")) {
      tooltip.addAll(Tooltip.get(unlocalizedName + ".RedLogic"))
    }
    if (Loader.isModLoaded("MineFactoryReloaded")) {
      tooltip.addAll(Tooltip.get(unlocalizedName + ".RedNet"))
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def registerIcons(iconRegister: IconRegister) {
    super.registerIcons(iconRegister)

    icon = iconRegister.registerIcon(Settings.resourceDomain + ":card_redstone")
  }
}
