/*
 *  Sources of clock-offset information for Scala/Android NTP application
 *  RW Penney, January 2016
 */

package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.location.{Location, LocationListener, LocationManager}
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
    case BriefUpdateRequest =>  briefUpdate()
    case pos: GeoPos =>         deviceMovedTo(pos)
    case ShutdownRequest =>     context.stop(self)
  }

  def update(): Unit
  def deviceMovedTo(pos: GeoPos): Unit = {}
  def briefUpdate(): Unit = {}

  override def postStop(): Unit = cancelScheduled()
}


/**
 *  Clock-offset estimator using Network Time Protocol,
 *  using multiple network hosts.
 */
class NTPtimeRef(ntpmap: NtpZones, fuser: ActorRef)
        extends TimeRef(fuser) {
  var ntpAddresses: Seq[InetAddress] = Nil
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

  override def deviceMovedTo(pos: GeoPos) {
    val (hosts, zones) = ntpmap.getHosts(pos)

    ntpAddresses = addrLookup(hosts)

    val nHosts = hosts.length
    val zoneSummary = zones.mkString("{ ", ", ", " }")

    fuser ! NtpStatus(s"${nHosts} NTP servers\nZones: ${zoneSummary}")
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
                                        stddev_ms=delay.toDouble)
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
