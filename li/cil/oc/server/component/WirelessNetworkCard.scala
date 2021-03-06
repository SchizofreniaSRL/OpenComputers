package li.cil.oc.server.component

import java.io._
import java.net.{HttpURLConnection, URL}
import java.nio.charset.MalformedInputException
import java.util.concurrent.Future
import java.util.regex.Matcher
import li.cil.oc.api.network._
import li.cil.oc.util.{ThreadPoolFactory, WirelessNetwork}
import li.cil.oc.{Settings, api}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.tileentity.TileEntity
import scala.collection.convert.WrapAsScala._
import scala.language.implicitConversions

class WirelessNetworkCard(val owner: TileEntity) extends NetworkCard {
  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("modem", Visibility.Neighbors).
    withConnector().
    create()

  var strength = 0.0

  var response: Option[Future[_]] = None

  // ----------------------------------------------------------------------- //

  @LuaCallback(value = "getStrength", direct = true)
  def getStrength(context: Context, args: Arguments): Array[AnyRef] = result(strength)

  @LuaCallback("setStrength")
  def setStrength(context: Context, args: Arguments): Array[AnyRef] = {
    strength = math.max(args.checkDouble(0), math.min(0, Settings.get.maxWirelessRange))
    result(strength)
  }

  override def isWireless(context: Context, args: Arguments): Array[AnyRef] = result(true)

  @LuaCallback(value = "isHttpEnabled", direct = true)
  def isHttpEnabled(context: Context, args: Arguments): Array[AnyRef] = result(Settings.get.httpEnabled)

  override def send(context: Context, args: Arguments) = {
    val address = args.checkString(0)
    if (isHttpRequest(address)) {
      checkAddress(address)
      val post = if (args.isString(1)) Option(args.checkString(1)) else None
      WirelessNetworkCard.threadPool.submit(new Runnable {
        def run() = try {
          val proxy = Option(MinecraftServer.getServer.getServerProxy).getOrElse(java.net.Proxy.NO_PROXY)
          val url = new URL(address)
          url.openConnection(proxy) match {
            case http: HttpURLConnection => try {
              http.setDoInput(true)
              if (post.isDefined) {
                http.setRequestMethod("POST")
                http.setDoOutput(true)

                val out = new BufferedWriter(new OutputStreamWriter(http.getOutputStream))
                out.write(post.get)
                out.close()
              }
              else {
                http.setRequestMethod("GET")
                http.setDoOutput(false)
              }
              var part: Option[String] = None
              for (line <- scala.io.Source.fromInputStream(http.getInputStream).getLines()) {
                if ((part + line).length <= Settings.get.maxNetworkPacketSize) {
                  part = Some(part.getOrElse("") + line + "\n")
                }
                else {
                  context.signal("http_response", address, part.get)
                  part = None
                }
              }
              context.signal("http_response", address, part.orNull)
              if (part.isDefined) {
                context.signal("http_response", address)
              }
            }
            finally {
              http.disconnect()
            }
            case other => context.signal("http_response", address, Unit, "connection failed")
          }
        }
        catch {
          case e: FileNotFoundException =>
            context.signal("http_response", address, Unit, "not found: " + Option(e.getMessage).getOrElse(e.toString))
          case _: MalformedInputException =>
            context.signal("http_response", address, Unit, "binary data not yet supported")
          case e: Throwable =>
            context.signal("http_response", address, Unit, Option(e.getMessage).getOrElse(e.toString))
        }
      })
      result(true)
    }
    else {
      val port = checkPort(args.checkInteger(1))
      checkPacketSize(args.drop(2))
      if (strength > 0) {
        checkPower()
        for ((card, distance) <- WirelessNetwork.computeReachableFrom(this)
             if card.node.address == address && card.openPorts.contains(port)) {
          card.node.sendToReachable("computer.signal",
            Seq("modem_message", node.address, Int.box(port), Double.box(distance)) ++ args.drop(2): _*)
        }
      }
      super.send(context, args)
    }
  }

  override def broadcast(context: Context, args: Arguments) = {
    val port = checkPort(args.checkInteger(0))
    checkPacketSize(args.drop(1))
    if (strength > 0) {
      checkPower()
      for ((card, distance) <- WirelessNetwork.computeReachableFrom(this)
           if card.openPorts.contains(port)) {
        card.node.sendToReachable("computer.signal",
          Seq("modem_message", node.address, Int.box(port), Double.box(distance)) ++ args.drop(1): _*)
      }
    }
    super.broadcast(context, args)
  }

  private def checkPower() {
    val cost = Settings.get.wirelessCostPerRange
    if (cost > 0 && !Settings.get.ignorePower) {
      if (!node.tryChangeBuffer(-strength * cost)) {
        throw new IOException("not enough energy")
      }
    }
  }

  private def isHttpRequest(address: String) = {
    try {
      new URL(address)
      true
    }
    catch {
      case e: Throwable => false
    }
  }

  private def checkAddress(address: String) {
    val url = new URL(address)
    val protocol = url.getProtocol
    if (!protocol.matches("^https?$")) {
      throw new FileNotFoundException("unsupported protocol")
    }
    val host = Matcher.quoteReplacement(url.getHost)
    if (Settings.get.httpHostWhitelist.length > 0 && !Settings.get.httpHostWhitelist.exists(host.matches)) {
      throw new FileNotFoundException("domain is not whitelisted")
    }
    if (Settings.get.httpHostBlacklist.exists(host.matches)) {
      throw new FileNotFoundException("domain is blacklisted")
    }
  }

  // ----------------------------------------------------------------------- //

  override val canUpdate = true

  override def update() {
    super.update()
    WirelessNetwork.update(this)
  }

  override def onConnect(node: Node) {
    super.onConnect(node)
    if (node == this.node) {
      WirelessNetwork.add(this)
    }
  }

  override def onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (node == this.node) {
      val removed = WirelessNetwork.remove(this)
      assert(removed)
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    strength = nbt.getDouble("strength")
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    nbt.setDouble("strength", strength)
  }
}

object WirelessNetworkCard {
  private val threadPool = ThreadPoolFactory.create("HTTP", Settings.get.httpThreads)
}