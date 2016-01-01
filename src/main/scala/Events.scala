package uk.rwpenney.santp


sealed trait AkkaMessage

case object ClockTick extends AkkaMessage
case object UpdateRequest extends AkkaMessage
