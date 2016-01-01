package uk.rwpenney.santp

import akka.actor.Actor
import android.util.Log
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo => NTPTimeInfo}


trait TimeRef extends Actor {
  def receive = {
    case UpdateRequest => update()
  }

  def update() : Unit
}


class NTPtimeRef extends TimeRef {
  def update() {
    Log.d("santp", "TICK")
  }
}


class GPStimeRef extends TimeRef {
  def update() {
  }
}
