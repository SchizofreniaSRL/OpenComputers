package li.cil.oc.common.tileentity

import li.cil.oc.api.driver
import li.cil.oc.api.driver.{Item => ItemDriver}
import li.cil.oc.api.network
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.driver.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.{TileEntity => MCTileEntity}
import scala.collection.mutable

trait ComponentInventory extends Inventory with network.Environment { self: MCTileEntity =>
  protected lazy val components = Array.fill[Option[ManagedEnvironment]](getSizeInventory)(None)
  protected val updatingComponents = mutable.ArrayBuffer.empty[ManagedEnvironment]

  // ----------------------------------------------------------------------- //

  def installedMemory = items.foldLeft(0)((sum, stack) => sum + (stack match {
    case Some(item) => Registry.driverFor(item) match {
      case Some(driver: driver.Memory) => driver.amount(item)
      case _ => 0
    }
    case _ => 0
  }))

  // ----------------------------------------------------------------------- //

  abstract override def onConnect(node: Node) {
    super.onConnect(node)
    if (node == this.node) {
      for ((stack, slot) <- items.zipWithIndex collect {
        case (Some(stack), slot) if slot >= 0 && slot < components.length => (stack, slot)
      } if components(slot).isEmpty && isComponentSlot(slot)) {
        components(slot) = Registry.driverFor(stack) match {
          case Some(driver) =>
            Option(driver.createEnvironment(stack, this)) match {
              case Some(component) =>
                component.load(dataTag(driver, stack))
                if (component.canUpdate) {
                  assert(!updatingComponents.contains(component))
                  updatingComponents += component
                }
                Some(component)
              case _ => None
            }
          case _ => None
        }
      }
      components collect {
        case Some(component) => connectItemNode(component.node)
      }
    }
  }

  abstract override def onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (node == this.node) {
      components collect {
        case Some(component) => component.node.remove()
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def save(nbt: NBTTagCompound) = {
    items.zipWithIndex collect {
      case (Some(stack), slot) => (stack, slot)
    } foreach {
      case (stack, slot) => components(slot) match {
        case Some(environment) =>
          // We're guaranteed to have a driver for entries.
          environment.save(dataTag(Registry.driverFor(stack).get, stack))
        case _ => // Nothing special to save.
      }
    }
    super.save(nbt) // Save items after updating their tags.
  }

  // ----------------------------------------------------------------------- //

  def getInventoryStackLimit = 1

  override protected def onItemAdded(slot: Int, stack: ItemStack) = if (isServer && isComponentSlot(slot)) {
    Registry.driverFor(stack) match {
      case Some(driver) => Option(driver.createEnvironment(stack, this)) match {
        case Some(component) => this.synchronized {
          components(slot) = Some(component)
          component.load(dataTag(driver, stack))
          connectItemNode(component.node)
          if (component.canUpdate) {
            assert(!updatingComponents.contains(component))
            updatingComponents += component
          }
          component.save(dataTag(driver, stack))
        }
        case _ => // No environment (e.g. RAM).
      }
      case _ => // No driver.
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack) = if (isServer) {
    // Uninstall component previously in that slot.
    components(slot) match {
      case Some(component) => this.synchronized {
        // Note to self: we have to remove the node from the network *before*
        // saving, to allow file systems to close their handles before they
        // are saved (otherwise hard drives would restore all handles after
        // being installed into a different computer, even!)
        components(slot) = None
        updatingComponents -= component
        component.node.remove()
        Registry.driverFor(stack).foreach(driver =>
          component.save(dataTag(driver, stack)))
      }
      case _ => // Nothing to do.
    }
  }

  protected def isComponentSlot(slot: Int) = true

  protected def connectItemNode(node: Node) {
    this.node.connect(node)
  }

  protected def dataTag(driver: ItemDriver, stack: ItemStack) =
    Option(driver.dataTag(stack)).getOrElse(Item.dataTag(stack))
}