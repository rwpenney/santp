package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log
import java.net.InetAddress
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo => NTPTimeInfo}
import scala.concurrent.duration._


abstract class TimeRef(fuser: ActorRef) extends Actor with CancellableScheduler {
  def receive = {
    case UpdateRequest => {
      update()
      scheduleOnce(context.system, FiniteDuration(5, SECONDS),
                   self, UpdateRequest)
    }
    case ShutdownRequest => context.stop(self)
  }

  def update(): Unit

  override def postStop(): Unit = cancelScheduled()
}


class NTPtimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  val (ntpClient, hostaddr) = initClient("1.uk.pool.ntp.org")

  def update() {
    import java.lang.NullPointerException
    import java.io.IOException

    Log.d(Config.LogName, "NTPtimeRef.update()")

    try {
      val info = ntpClient.getTime(hostaddr)

      val offset = info.getOffset().toDouble
      val delay = info.getDelay().toDouble

      Log.d(Config.LogName, s"NTPtimeRef gave offset=${offset}, delay=${delay}".format(offset, delay))

      fuser ! OffsetModel(offset, stddev_ms=0.5*delay)
    } catch {
      case ex: IOException => Log.e(Config.LogName, s"NTP I/O error: ${ex}")
      case ex: NullPointerException => Log.e(Config.LogName, s"NTP error: ${ex}")
    }
  }

  def initClient(hostname: String): (NTPUDPClient, InetAddress) = {
    Log.d(Config.LogName, s"Initializing NTP connection for ${hostname}")

    val client = new NTPUDPClient()
    client.setDefaultTimeout(2000)
    val addr = InetAddress.getByName(hostname)

    (client, addr)
  }
}


class GPStimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  def update() {
    Log.d(Config.LogName, "GPStimeRef.update()")

    // FIXME - get time from GPS
    fuser ! OffsetModel(0)
  }
}


class DebugTimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  def update() {
    Log.d(Config.LogName, "DebugTimeRef.update()")

    fuser ! OffsetModel(sigma * randgen.nextGaussian(),
                        stddev_ms=sigma)
  }

  val sigma = 20 * 1000
  val randgen = new scala.util.Random()
}
