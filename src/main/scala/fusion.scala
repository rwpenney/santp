package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef}
import android.util.Log


/**
 *  Mechanism for receiving and merging clock-offset models
 *  received as Akka messages, and sending a fused model
 *  to a single consumer.
 *
 *  See [[OffsetModel#degrade]].
 */
case class TimeRefFuser(consumer: ActorRef,
                        model: OffsetModel=OffsetModel(),
                        drifttime_ms: Double=10*60*1000) extends Actor {
  val MinError = 5
  private var mergedModel = model

  def receive = {
    case newmodel: OffsetModel => {
      Log.d(Config.LogName, "TimeRefFuser received update" +
                            " from " + sender.toString)
      val degraded = mergedModel.degrade(newmodel.last_update, drifttime_ms)
      mergedModel = combine(degraded, newmodel)
      consumer ! mergedModel
    }
  }

  def combine(m1: OffsetModel, m2: OffsetModel) = {
    val merged = m1 * m2
    if (merged.stddev_ms > MinError) {
      merged
    } else {
      merged.copy(stddev_ms = MinError)
    }
  }
}


/**
 *  Recursive estimator of mean and standard-deviation
 *  of a sequence of samples, using exponential smoothing.
 */
class ExpoAverager(var mean: Double, var stddev: Double,
                   val weight: Double) {
  private var variance = stddev * stddev

  def apply(datum: Double) = {
    val delta = datum - mean
    variance += weight * (delta * delta)
    mean += weight * delta
    stddev = Math.sqrt(variance)
    this
  }
}
