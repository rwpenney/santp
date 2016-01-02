package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo => NTPTimeInfo}


abstract class TimeRef(fuser: ActorRef) extends Actor {
  def receive = {
    case UpdateRequest => update()
  }

  def update() : Unit
}


class NTPtimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  def update() {
    Log.d(Config.LogName, "NTPtimeRef.update()")

    // FIXME - do NTP request
    fuser ! OffsetModel(0)
  }
}


class GPStimeRef(fuser: ActorRef) extends TimeRef(fuser) {
  def update() {
    Log.d(Config.LogName, "GPStimeRef.update()")

    // FIXME - get time from GPS
    fuser ! OffsetModel(0)
  }
}
