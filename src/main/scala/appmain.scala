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
  val instanceId = f"${(System.currentTimeMillis & 0xffffff)}%06x"
  lazy val actorSys = ActorSystem(s"santpSystem-${instanceId}")

  lazy val timeText = findView[TextView](R.id.txt_time)
  lazy val offsetText = findView[TextView](R.id.txt_offset)
  lazy val offerrText = findView[TextView](R.id.txt_offerr)
  lazy val offcntText = findView[TextView](R.id.txt_offcnt)
  lazy val offageText = findView[TextView](R.id.txt_offage)

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

    initActors()
  }

  override def onStart() {
    super.onStart()

    Log.d(Config.LogName, "Starting NTP timer reference")
  }

  override def onStop() {
    Log.d(Config.LogName, "Stopping SANTP")

    super.onStop()
  }

  override def onDestroy() {
    stopActors()

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

    val ntpHosts = Seq("1.uk.pool.ntp.org", "1.ie.pool.ntp.org",
                       "2.fr.pool.ntp.org", "3.ch.pool.ntp.org",
                       "0.europe.pool.ntp.org",
                       "1.ca.pool.ntp.org",
                       "3.north-america.pool.ntp.org",
                       "2.debian.pool.ntp.org")
    timeRefs += actorSys.actorOf(AkkaProps(classOf[NTPtimeRef],
                                           fuser, ntpHosts), "NTPref")

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
