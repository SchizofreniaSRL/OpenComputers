package li.cil.oc

import cpw.mods.fml.common.registry.GameRegistry
import li.cil.oc.common.block._
import li.cil.oc.common.tileentity

object Blocks {
  var blockSimple: SimpleDelegator = _
  var blockSimpleWithRedstone: SimpleDelegator = _
  var blockSpecial: SpecialDelegator = _
  var blockSpecialWithRedstone: SpecialDelegator = _

  var adapter: Adapter = _
  var cable: Cable = _
  var capacitor: Capacitor = _
  var charger: Charger = _
  var case1, case2, case3: Case = _
  var diskDrive: DiskDrive = _
  var keyboard: Keyboard = _
  var powerDistributor: PowerDistributor = _
  var powerConverter: PowerConverter = _
  var redstone: Redstone = _
  var robotProxy: RobotProxy = _
  var robotAfterimage: RobotAfterimage = _
  var router: Router = _
  var screen1, screen2, screen3: Screen = _

  def init() {
    blockSimple = new SimpleDelegator(Settings.get.blockId1)
    blockSimpleWithRedstone = new SimpleRedstoneDelegator(Settings.get.blockId2)
    blockSpecial = new SpecialDelegator(Settings.get.blockId3)
    blockSpecialWithRedstone = new SpecialRedstoneDelegator(Settings.get.blockId4)

    GameRegistry.registerBlock(blockSimple, classOf[Item], Settings.namespace + "simple")
    GameRegistry.registerBlock(blockSimpleWithRedstone, classOf[Item], Settings.namespace + "simple_redstone")
    GameRegistry.registerBlock(blockSpecial, classOf[Item], Settings.namespace + "special")
    GameRegistry.registerBlock(blockSpecialWithRedstone, classOf[Item], Settings.namespace + "special_redstone")

    GameRegistry.registerTileEntity(classOf[tileentity.Adapter], Settings.namespace + "adapter")
    GameRegistry.registerTileEntity(classOf[tileentity.Cable], Settings.namespace + "cable")
    GameRegistry.registerTileEntity(classOf[tileentity.Capacitor], Settings.namespace + "capacitor")
    GameRegistry.registerTileEntity(classOf[tileentity.Case], Settings.namespace + "case")
    GameRegistry.registerTileEntity(classOf[tileentity.Charger], Settings.namespace + "charger")
    GameRegistry.registerTileEntity(classOf[tileentity.DiskDrive], Settings.namespace + "disk_drive")
    GameRegistry.registerTileEntity(classOf[tileentity.Keyboard], Settings.namespace + "keyboard")
    GameRegistry.registerTileEntity(classOf[tileentity.PowerConverter], Settings.namespace + "power_converter")
    GameRegistry.registerTileEntity(classOf[tileentity.PowerDistributor], Settings.namespace + "power_distributor")
    GameRegistry.registerTileEntity(classOf[tileentity.Redstone], Settings.namespace + "redstone")
    GameRegistry.registerTileEntity(classOf[tileentity.RobotProxy], Settings.namespace + "robot")
    GameRegistry.registerTileEntity(classOf[tileentity.Router], Settings.namespace + "router")
    GameRegistry.registerTileEntity(classOf[tileentity.Screen], Settings.namespace + "screen")

    // IMPORTANT: the multi block must come first, since the sub blocks will
    // try to register with it. Also, the order the sub blocks are created in
    // must not be changed since that order determines their actual IDs.
    adapter = new Adapter(blockSimple)
    cable = new Cable(blockSpecial)
    capacitor = new Capacitor(blockSimple)
    case1 = new Case.Tier1(blockSimpleWithRedstone)
    case2 = new Case.Tier2(blockSimpleWithRedstone)
    case3 = new Case.Tier3(blockSimpleWithRedstone)
    charger = new Charger(blockSimpleWithRedstone)
    diskDrive = new DiskDrive(blockSimple)
    keyboard = new Keyboard(blockSpecial)
    powerDistributor = new PowerDistributor(blockSimple)
    powerConverter = new PowerConverter(blockSimple)
    robotAfterimage = new RobotAfterimage(blockSpecial)
    robotProxy = new RobotProxy(blockSpecialWithRedstone)
    router = new Router(blockSimple)
    screen1 = new Screen.Tier1(blockSimple)
    screen2 = new Screen.Tier2(blockSimple)
    screen3 = new Screen.Tier3(blockSimple)

    redstone = new Redstone(blockSimpleWithRedstone)
  }
}