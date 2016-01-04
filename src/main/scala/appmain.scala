/*
 *  Scala/Android app for NTP-based time synchronization
 *  RW Penney, December 2015
 */

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


/**
 *  Akka event-dispatch mechanism for top-level Activity.
 */
case class UIupdater(ui: SantpActivity)
        extends Actor with CancellableScheduler {
  def receive = {
    case ClockTick => {
      val correctedTime = ui.onClockTick()
      val nextTick = 1000 - (correctedTime % 1000)
      scheduleOnce(context.system, FiniteDuration(nextTick, MILLISECONDS),
                   self, ClockTick)
    }
    case model: OffsetModel => ui.updateModel(model)
    case ShutdownRequest => context.stop(self)
  }

  override def postStop(): Unit = cancelScheduled()
}


/**
 *  Top-level Activity for SANTP app.
 */
class SantpActivity extends SActivity {
  lazy val actorSys = ActorSystem("santpSystem")

  lazy val timeText = new STextView("00:00:00") textSize 40.dip
  lazy val offsetText = new STextView("??")
  lazy val offerrText = new STextView("??")
  lazy val offcntText = new STextView("??")
  lazy val offageText = new STextView("??")

  val timeRefs = MutableList[ActorRef]()
  var uiUpdater: ActorRef = null
  var fuser: ActorRef = null
  var timeCorrection = OffsetModel(stddev_ms=5*60*1000)
  var timeFormatter = new SimpleDateFormat("HH:mm:ss")

  onCreate {
    Log.d(Config.LogName, "Creating SANTP")

    contentView = new SVerticalLayout {
      this += timeText

      new STableLayout {
        style {
          case t: STextView => t.textSize(15.dip)
        }
        this += new STableRow {
          STextView("offset:")
          offsetText . here
          STextView("stddev:")
          offerrText . here
        } . wrap
        this += new STableRow {
          STextView("count:")
          offcntText . here
          STextView("age:")
          offageText . here
        } . wrap
      } . wrap . here
    }
  }

  onStart {
    Log.d(Config.LogName, "Starting NTP timer reference")

    initActors()
  }

  onStop {
    Log.d(Config.LogName, "Stopping SANTP")

    stopActors()
  }

  onDestroy {
    actorSys.shutdown
    actorSys.awaitTermination
  }

  def onClockTick(): Long = {
    val sysTime = System.currentTimeMillis
    val correctedTime = timeCorrection(sysTime)
    val timeStr = timeFormatter.format(new Date(correctedTime))
    val age = (sysTime - timeCorrection.last_update) / 1000

    runOnUiThread({
      timeText.text(timeStr)
      offageText.text(s"${age}s")
    })

    correctedTime
  }

  def updateModel(model: OffsetModel) {
    Log.d(Config.LogName, "Installing new OffsetModel")

    timeCorrection = model

    runOnUiThread({
      offsetText.text(s"${timeCorrection.offset_ms.toInt}ms")
      offerrText.text(s"${timeCorrection.stddev_ms.toInt}ms")
      offcntText.text(s"${timeCorrection.n_measurements}")
    })
  }

  def initActors() {
    uiUpdater = actorSys.actorOf(AkkaProps(classOf[UIupdater], this),
                                 "UIupdater")
    fuser = actorSys.actorOf(AkkaProps(classOf[TimeRefFuser], uiUpdater),
                                 "TimeRefFuser")

    timeRefs += actorSys.actorOf(AkkaProps(classOf[NTPtimeRef], fuser),
                                 "NTPref")

    timeRefs.foreach(tr => tr ! UpdateRequest)
    uiUpdater ! ClockTick
  }

  def stopActors() {
    // Send shutdown signals to Actors which use scheduled messages:
    timeRefs.foreach(tr => tr ! ShutdownRequest)
    uiUpdater ! ShutdownRequest
  }
}
