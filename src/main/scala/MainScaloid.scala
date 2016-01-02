package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef, ActorSystem, Props => AkkaProps }
import android.graphics.Color
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import org.scaloid.common._
import scala.collection.mutable.MutableList
import scala.concurrent.duration._


object Config {
  val LogName = "ScalaAndroidNTP"
}


case class UIupdater(ui: MainScaloid) extends Actor {
  import scala.concurrent.ExecutionContext.Implicits.global

  def receive = {
    case ClockTick => {
      val correctedTime = ui.onClockTick()
      val nextTick = 1000 - (correctedTime % 1000)
      context.system.scheduler.scheduleOnce(nextTick milliseconds,
                                            self, ClockTick)
    }
    case model: OffsetModel => ui.updateModel(model)
  }
}



class MainScaloid extends SActivity {
  lazy val actorSys = ActorSystem("santpSystem")
  lazy val timeText = new STextView("00:00:00") textSize 40.dip
  val timeRefs = MutableList[ActorRef]()
  var uiUpdater: ActorRef = null
  var timeCorrection = OffsetModel()
  var timeFormatter = new SimpleDateFormat("HH:mm:ss")

  onCreate {
    Log.d(Config.LogName, "Creating SANTP")

    contentView = new SVerticalLayout {
      timeText.here

      new SVerticalLayout {
        style {
          case t: STextView => t textSize 20.dip
          case e: SEditText => e.backgroundColor(Color.YELLOW).textColor(Color.BLACK)
        }
        STextView("I am 20 dip tall")
        STextView("I am 25 dip tall") textSize 25.dip // overriding
        new SLinearLayout {
          STextView("Button: ")
          SButton(R.string.red)
        }.wrap.here
      }.padding(20.dip).here
    }
  }

  onStart {
    Log.d(Config.LogName, "Starting NTP timer reference")

    initActors()
  }

  onDestroy {
    Log.d(Config.LogName, "Destroying SANTP")

    actorSys.shutdown
  }

  def onClockTick(): Long = {
    val correctedTime = timeCorrection(compat.Platform.currentTime)
    val timeStr = timeFormatter.format(new Date(correctedTime))
    runOnUiThread(timeText.text(timeStr))
    correctedTime
  }

  def updateModel(model: OffsetModel) {
    Log.d(Config.LogName, "Installing new OffsetModel")
    timeCorrection = model
  }

  def initActors() {
    import scala.concurrent.ExecutionContext.Implicits.global

    uiUpdater = actorSys.actorOf(AkkaProps(classOf[UIupdater], this),
                                 "UIupdater")
    actorSys.scheduler.scheduleOnce(10 milliseconds, uiUpdater, ClockTick)
    val fuser = actorSys.actorOf(AkkaProps(classOf[TimeRefFuser], uiUpdater),
                                 "TimeRefFuser")

    timeRefs += actorSys.actorOf(AkkaProps(classOf[NTPtimeRef], fuser),
                                 "NTPref")

    timeRefs.foreach(tr =>
        actorSys.scheduler.schedule(100 milliseconds, 5 seconds,
                                    tr, UpdateRequest)
    )
  }
}
