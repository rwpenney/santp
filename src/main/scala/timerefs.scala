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
      scheduleOnce(context.system, FiniteDuration(10, MINUTES),
                   self, UpdateRequest)
    }
    case ShutdownRequest => context.stop(self)
  }

  def update(): Unit

  override def postStop(): Unit = cancelScheduled()
}


class NTPtimeRef(fuser: ActorRef,
                 hosts: Seq[String]) extends TimeRef(fuser) {
  val ntpAddresses = addrLookup(hosts)
  val ntpClient = initClient()

  def update() {
    Log.d(Config.LogName, "NTPtimeRef.update()")

    ntpAddresses.foreach { addr =>
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
        case ex: Exception => Log.e(Config.LogName, s"NTP error: ${ex}")
      }
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
