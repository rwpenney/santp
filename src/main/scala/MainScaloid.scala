package uk.rwpenney.santp

import akka.actor.{ActorRef, ActorSystem, Props => AkkaProps }
import android.graphics.Color
import android.util.Log
import org.scaloid.common._
import scala.collection.mutable.MutableList
import scala.concurrent.duration._


class MainScaloid extends SActivity {
  lazy val actorSys = ActorSystem("santpSystem")
  lazy val meToo = new STextView("Me too")
  val timeRefs = MutableList[ActorRef]()

  onCreate {
    contentView = new SVerticalLayout {
      style {
        case b: SButton => b.textColor(Color.RED).onClick(meToo.text = "PRESSED")
        case t: STextView => t textSize 20.dip
        case e: SEditText => e.backgroundColor(Color.YELLOW).textColor(Color.BLACK)
      }
      STextView("I am 20 dip tall")
      meToo.here
      STextView("I am 25 dip tall") textSize 25.dip // overriding
      new SLinearLayout {
        STextView("Button: ")
        SButton(R.string.red)
      }.wrap.here
    } padding 20.dip

    Log.d("santp", "Starting NTP timer reference")
    initActors()
  }

  def initActors() {
    import scala.concurrent.ExecutionContext.Implicits.global

    timeRefs += actorSys.actorOf(AkkaProps(classOf[NTPtimeRef]), "NTPref")

    timeRefs.foreach(tr =>
        actorSys.scheduler.schedule(100 milliseconds, 5 seconds,
                                    tr, UpdateRequest)
    )
  }
}
