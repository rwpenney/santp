package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log


case class TimeRefFuser(consumer: ActorRef,
                        model: OffsetModel=OffsetModel()) extends Actor {
  val MinError = 5
  private var mergedModel = model

  def receive = {
    case newmodel: OffsetModel => {
      Log.d(Config.LogName, "TimeRefFuser received update from " + sender.toString)
      mergedModel = combine(mergedModel, newmodel)
      consumer ! mergedModel
    }
  }

  def combine(m1: OffsetModel, m2: OffsetModel) = {
    val merged = m1 * m2
    // FIXME - degrade current model based on age
    if (merged.stddev_ms > MinError) {
      merged
    } else {
      merged.copy(stddev_ms = MinError)
    }
  }
}
