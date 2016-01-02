package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log


case class TimeRefFuser(consumer: ActorRef) extends Actor {
  private var mergedModel = OffsetModel()

  def receive = {
    case newmodel: OffsetModel => {
      Log.d(Config.LogName, "TimeRefFuser received update from " + sender.toString)
      // FIXME - merge model with master
      consumer ! newmodel
    }
  }
}
