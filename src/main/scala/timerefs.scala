package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.location.{LocationListener, LocationManager}
import android.util.Log
import java.net.InetAddress
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo => NTPTimeInfo}
import scala.concurrent.duration._
import scala.util.Random


/**
 *  Abstract source of clock-offset measurements,
 *  providing Akka messages to a fusion engine.
 *
 *  See [[TimeRefFuser]].
 */
abstract class TimeRef(fuser: ActorRef) extends Actor with CancellableScheduler {
  def receive = {
    case UpdateRequest => {
      update()
      scheduleOnce(context.system, FiniteDuration(20, MINUTES),
                   self, UpdateRequest)
      Seq(1, 2, 5, 10).foreach { delay =>
        scheduleOnce(context.system, FiniteDuration(delay, MINUTES),
                     self, BriefUpdateRequest)
      }
    }
    case BriefUpdateRequest => {
      briefUpdate()
    }
    case ShutdownRequest => context.stop(self)
  }

  def update(): Unit
  def briefUpdate(): Unit = {}

  override def postStop(): Unit = cancelScheduled()
}


/**
 *  Clock-offset estimator using Network Time Protocol,
 *  using multiple network hosts.
 */
class NTPtimeRef(fuser: ActorRef,
                 hosts: Seq[String])
    extends TimeRef(fuser) {
  val ntpAddresses = addrLookup(hosts)
  val ntpClient = initClient()

  def update() {
    Log.d(Config.LogName, "NTPtimeRef.update()")

    ntpAddresses.foreach {
        addr => updateFromHost(addr)
    }
  }

  override def briefUpdate() {
    Log.d(Config.LogName, "NTPtimeRef.briefUpdate()")

    val addrs = Random.shuffle(ntpAddresses)

    if (addrs.length > 0) {
      updateFromHost(addrs.head)
    }
  }

  def updateFromHost(addr: InetAddress) {
    try {
      val info = ntpClient.getTime(addr)
      info.computeDetails()

      val offdel = (Option(info.getOffset()),
                    Option(info.getDelay()))

      Log.d(Config.LogName, s"NTPtimeRef gave offset=${offdel}")

      offdel match {
        case (Some(offset), Some(delay)) =>
                    fuser ! OffsetModel(offset.toDouble,
                                        stddev_ms=0.5*delay.toDouble)
        case _ =>   // Ignore
      }
    } catch {
      case ex: Exception =>
                    Log.e(Config.LogName,
                          s"NTP error polling '${addr.getHostName}': ${ex}")
    }
  }

  def initClient(): NTPUDPClient = {
    val client = new NTPUDPClient()

    client.setDefaultTimeout(2000)
    client.open()

    Log.d(Config.LogName, s"Initialized NTP socket ${client.getLocalPort()}")

    client
  }

  def addrLookup(hosts: Seq[String]): Seq[InetAddress] = {
    hosts.flatMap { h =>
      try {
        Some(InetAddress.getByName(h))
      } catch {
        case ex: Exception => None
      }
    }
  }
}


/**
 *  Mixin to assist with converting GPS location updates
 *  into clock-offset measurements
 *
 *  See [[TimeRef]].
 */
trait GPSrefHelper extends LocationListener {
  val GPSdeltaStats = new ExpoAverager(0, 100, 0.2)

  def requestGPSupdates(lm: LocationManager, interval_s: Int=19) {
    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                              interval_s * 1000, 0.0f, this)
  }

  def onProviderDisabled(p: String) {}
  def onProviderEnabled(p: String) {}
  def onStatusChanged(p: String, status: Int, extras: android.os.Bundle) {}
}


/**
 *  Randomized clock-offset measurement source,
 *  typically for debugging.
 */
class DebugTimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  def update() {
    Log.d(Config.LogName, "DebugTimeRef.update()")

    fuser ! OffsetModel(sigma * randgen.nextGaussian(),
                        stddev_ms=sigma)
  }

  val sigma = 20 * 1000
  val randgen = new scala.util.Random()
}
