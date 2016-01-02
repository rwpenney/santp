package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo => NTPTimeInfo}
import scala.concurrent.duration._


abstract class TimeRef(fuser: ActorRef) extends Actor with CancellableScheduler {
  def receive = {
    case UpdateRequest => {
      update()
      scheduleOnce(context.system, 5 seconds, self, UpdateRequest)
    }
    case ShutdownRequest => context.stop(self)
  }

  def update(): Unit

  override def postStop(): Unit = cancelScheduled()
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
