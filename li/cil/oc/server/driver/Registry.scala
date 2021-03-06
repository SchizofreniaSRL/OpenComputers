package li.cil.oc.server.driver

import li.cil.oc.api
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import scala.Some
import scala.collection.mutable.ArrayBuffer

/**
 * This class keeps track of registered drivers and provides installation logic
 * for each registered driver.
 *
 * Each component type must register its driver with this class to be used with
 * computers, since this class is used to determine whether an object is a
 * valid component or not.
 *
 * All drivers must be installed once the game starts - in the init phase - and
 * are then injected into all computers started up past that point. A driver is
 * a set of functions made available to the computer. These functions will
 * usually require a component of the type the driver wraps to be installed in
 * the computer, but may also provide context-free functions.
 */
private[oc] object Registry extends api.detail.DriverAPI {
  /** The list of registered block drivers. */
  private val blocks = ArrayBuffer.empty[api.driver.Block]

  /** The list of registered item drivers. */
  private val items = ArrayBuffer.empty[api.driver.Item]

  /** Used to keep track of whether we're past the init phase. */
  var locked = false

  /**
   * Registers a new driver for a block component.
   *
   * Whenever the neighboring blocks of a computer change, it checks if there
   * exists a driver for the changed block, and if so installs it.
   *
   * @param driver the driver for that block type.
   */
  def add(driver: api.driver.Block) {
    if (locked) throw new IllegalStateException("Please register all drivers in the init phase.")
    if (!blocks.contains(driver)) blocks += driver
  }

  /**
   * Registers a new driver for an item component.
   *
   * Item components can inserted into a computers component slots. They have
   * to specify their type, to determine into which slots they can fit.
   *
   * @param driver the driver for that item type.
   */
  def add(driver: api.driver.Item) {
    if (locked) throw new IllegalStateException("Please register all drivers in the init phase.")
    if (!blocks.contains(driver)) items += driver
  }

  /**
   * Used when a new block is placed next to a computer to see if we have a
   * driver for it. If we have one, we'll return it.
   *
   * @param world the world in which the block to check lives.
   * @param x the X coordinate of the block to check.
   * @param y the Y coordinate of the block to check.
   * @param z the Z coordinate of the block to check.
   * @return the driver for that block if we have one.
   */
  def driverFor(world: World, x: Int, y: Int, z: Int) =
    blocks.find(_.worksWith(world, x, y, z)) match {
      case None => None
      case Some(driver) => Some(driver)
    }

  /**
   * Used when an item component is added to a computer to see if we have a
   * driver for it. If we have one, we'll return it.
   *
   * @param stack the type of item to check for a driver for.
   * @return the driver for that item type if we have one.
   */
  def driverFor(stack: ItemStack) =
    if (stack != null) items.find(_.worksWith(stack)) match {
      case None => None
      case Some(driver) => Some(driver)
    }
    else None
}
