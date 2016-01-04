/*
 *  Clock-offset models and handshaking classes for Scala/Android NTP app
 *  RW Penney, January 2016
 */
package uk.rwpenney.santp

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import scala.concurrent.duration.FiniteDuration
import android.util.Log


sealed trait AkkaMessage

case object ClockTick extends AkkaMessage
case object UpdateRequest extends AkkaMessage
case object ShutdownRequest extends AkkaMessage

/**
 *  Simple time-offset model of true time relative to system clock.
 *  Nominally, this is in the form of a Gaussian probability distribution.
 */
case class OffsetModel(offset_ms: Double=0.0,
                       stddev_ms: Double=0.0,
                       n_measurements: Int=0,
                       last_update: Long=0)
        extends AkkaMessage {
  def apply(sysTime: Long): Long = (sysTime + offset_ms.toLong)

  /**
   *  Combine two error-models, computing new mean and standard-deviation
   *  as product of two Gaussians.
   */
  def *(m: OffsetModel): OffsetModel = {
    val muA = offset_ms
    val muB = m.offset_ms
    val sigA = stddev_ms
    val sigB = m.stddev_ms

    val sig2 = (sigA * sigA + sigB * sigB)
    val mean = (muA * (sigB * sigB) + muB * (sigA * sigA)) / sig2
    val width = sigA * sigB / math.sqrt(sig2)

    OffsetModel(offset_ms=mean, stddev_ms=width,
                n_measurements=(n_measurements + m.n_measurements),
                last_update=math.max(last_update, m.last_update))
  }
}


trait CancellableScheduler {
  var task: Option[Cancellable] = None

  def scheduleOnce(sys: ActorSystem, delay: FiniteDuration,
                   receiver: ActorRef, message: Any): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    task = Some(sys.scheduler.scheduleOnce(delay, receiver, message))
  }

  def cancelScheduled(): Unit = {
    Log.d(Config.LogName, "cancelScheduled + " + task.toString)
    task.foreach(x => x.cancel)
    task = None
  }
}
