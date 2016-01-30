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
case object BriefUpdateRequest extends AkkaMessage
case object ShutdownRequest extends AkkaMessage

/**
 *  Simple time-offset model of true time relative to system clock.
 *  Nominally, this is in the form of a Gaussian probability distribution.
 *
 *  @param offset_ms      The estimated correction, in milliseconds,
 *                        that should be added to the local system time
 *                        to match the reference time.
 *  @param stddev_ms      The nominal standard-deviation of the clock offset,
 *                        in milliseconds.
 *  @param n_measurements The effective number of independent measurements
 *                        that have contributed to the current offset estimate.
 *  @param last_update    The time at which measurements were last fused
 *                        to form the current offset estimate.
 */
case class OffsetModel(offset_ms: Double=0.0,
                       stddev_ms: Double=0.0,
                       n_measurements: Int=1,
                       last_update: Long=System.currentTimeMillis)
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

    OffsetModel(offset_ms = mean, stddev_ms = width,
                n_measurements = (n_measurements + m.n_measurements),
                last_update = math.max(last_update, m.last_update))
  }

  /**
   *  Produce a degrade error-model by inflating the standard-deviation
   *  according to its time of last update relative to the timing
   *  of an observation from another clock reference,
   *  and a (diffusive) timescale.
   */
  def degrade(obstime: Long, drifttime_ms: Double): OffsetModel = {
    val age = obstime - last_update
    if (age <= 0) {
      this
    } else {
      val driftfrac = math.sqrt(1 + age / drifttime_ms)
      this.copy(stddev_ms = stddev_ms * driftfrac)
    }
  }
}


/**
 *  Representation of a point on the surface of a spherical Earth
 */
case class GeoPos(latitude: Double=0, longitude: Double=0) {
  val EarthRadius = 6371.0

  def separation(other: GeoPos) = {
    val v0 = toUnitVec
    val v1 = other.toUnitVec

    val dot = (v0 zip v1) map { case (c0, c1) => c0 * c1 } reduce(_ + _)

    if (Math.abs(dot) < 1.0) {
      Math.acos(dot) * EarthRadius
    } else {
      0.0
    }
  }

  /// Convert to a 3D Cartesian unit-vector
  def toUnitVec() = {
    val theta = Math.toRadians(90 - latitude)
    val phi = Math.toRadians(longitude)
    val cosTheta = Math.cos(theta)
    val sinTheta = Math.sin(theta)

    List(sinTheta * Math.cos(phi), sinTheta * Math.sin(phi), cosTheta)
  }
}


/**
 *  Akka mix-in to allow easy cancellation of a scheduled task.
 */
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
