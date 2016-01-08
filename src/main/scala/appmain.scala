/*
 *  Scala/Android app for NTP-based time synchronization
 *  RW Penney, December 2015
 */

package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef, ActorSystem, Props => AkkaProps }
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.lang.Runnable
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
class SantpActivity extends android.app.Activity {
  lazy val actorSys = ActorSystem("santpSystem")

  var timeText: TextView = null
  var offsetText: TextView = null
  var offerrText: TextView = null
  var offcntText: TextView = null
  var offageText: TextView = null

  val timeRefs = MutableList[ActorRef]()
  var uiUpdater: ActorRef = null
  var fuser: ActorRef = null
  var timeCorrection = OffsetModel(stddev_ms=5*60*1000, n_measurements=0,
                                   last_update=0)
  var timeFormatter = new SimpleDateFormat("HH:mm:ss")

  override def onCreate(b: android.os.Bundle) {
    super.onCreate(b)

    Log.d(Config.LogName, "Creating SANTP")

    setContentView(R.layout.appmain)

    timeText = findView[TextView](R.id.txt_time)
    offsetText = findView[TextView](R.id.txt_offset)
    offerrText = findView[TextView](R.id.txt_offerr)
    offcntText = findView[TextView](R.id.txt_offcnt)
    offageText = findView[TextView](R.id.txt_offage)
  }

  override def onStart() {
    super.onStart()

    Log.d(Config.LogName, "Starting NTP timer reference")

    initActors()
  }

  override def onStop() {
    Log.d(Config.LogName, "Stopping SANTP")

    stopActors()

    super.onStop()
  }

  override def onDestroy() {
    actorSys.shutdown
    actorSys.awaitTermination

    super.onDestroy()
  }

  def onClockTick(): Long = {
    val sysTime = System.currentTimeMillis
    val correctedTime = timeCorrection(sysTime)
    val timeStr = timeFormatter.format(new Date(correctedTime))
    val age = (sysTime - timeCorrection.last_update) / 1000

    runOnUiThread({
        timeText.setText(timeStr)
        offageText.setText(s"${age}s")
    })

    correctedTime
  }

  def updateModel(model: OffsetModel) {
    Log.d(Config.LogName, "Installing new OffsetModel")

    timeCorrection = model

    runOnUiThread({
        offsetText.setText(s"${timeCorrection.offset_ms.toInt}ms")
        offerrText.setText(s"${timeCorrection.stddev_ms.toInt}ms")
        offcntText.setText(s"${timeCorrection.n_measurements}")
    })
  }

  def initActors() {
    uiUpdater = actorSys.actorOf(AkkaProps(classOf[UIupdater], this),
                                 "UIupdater")
    fuser = actorSys.actorOf(AkkaProps(classOf[TimeRefFuser],
                                       uiUpdater, timeCorrection),
                                 "TimeRefFuser")

    timeRefs += actorSys.actorOf(AkkaProps(classOf[NTPtimeRef], fuser),
                                 "NTPref")
    timeRefs += actorSys.actorOf(AkkaProps(classOf[DebugTimeRef], fuser),
                                 "RandRef")

    timeRefs.foreach {
      tr => tr ! UpdateRequest
    }
    uiUpdater ! ClockTick
  }

  def stopActors() {
    // Send shutdown signals to Actors which use scheduled messages:
    timeRefs.foreach(tr => tr ! ShutdownRequest)
    uiUpdater ! ShutdownRequest
  }

  def findView[VT](id: Int): VT = findViewById(id).asInstanceOf[VT]

  def findViews[VT](ids: Seq[Int]): Seq[VT] = {
    for {
      id <- ids
    } yield findViewById(id).asInstanceOf[VT]
  }

  def runOnUiThread(f: => Unit): Unit = {
    runOnUiThread(
        new Runnable() {
          def run() { f }
        }
    )
  }
}
