package uk.rwpenney.santp

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import scala.concurrent.duration.FiniteDuration
import android.util.Log


sealed trait AkkaMessage

case object ClockTick extends AkkaMessage
case object UpdateRequest extends AkkaMessage
case object ShutdownRequest extends AkkaMessage

case class OffsetModel(offset_ms: Long=0) extends AkkaMessage {
  def apply(sysTime: Long) = (sysTime + offset_ms)
  // FIXME - include error estimate, etc.
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
