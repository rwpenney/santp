package uk.rwpenney.santp


sealed trait AkkaMessage

case object ClockTick extends AkkaMessage
case object UpdateRequest extends AkkaMessage

case class OffsetModel(offset_ms: Long=0) extends AkkaMessage {
  def apply(sysTime: Long) = (sysTime + offset_ms)
  // FIXME - include error estimate, etc.
}
