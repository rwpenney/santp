/*
 *  Scala/Android app for NTP-based time synchronization
 *  RW Penney, December 2015
 */

package uk.rwpenney.santp

import akka.actor.{Actor, ActorRef, ActorSystem, Props => AkkaProps }
import android.graphics.Color
import android.location.{Location, LocationListener, LocationManager}
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
  val TickPeriodMS = 200

  def receive = {
    case ClockTick => {
      val correctedTime = ui.onClockTick()
      val nextTick = TickPeriodMS - (correctedTime % TickPeriodMS)
      scheduleOnce(context.system, FiniteDuration(nextTick, MILLISECONDS),
                   self, ClockTick)
    }
    case model: OffsetModel =>  ui.updateModel(model)
    case pos: GeoPos =>         ui.displayLocation(pos)
    case NtpStatus(msg) =>      ui.displayNtpStatus(msg)
    case ShutdownRequest =>     context.stop(self)
  }

  override def postStop(): Unit = cancelScheduled()
}


/**
 *  Top-level Activity for SANTP app.
 */
class SantpActivity extends android.app.Activity with GeoLocator {
  val instanceId = f"${(System.currentTimeMillis & 0xffffff)}%06x"
  lazy val actorSys = ActorSystem(s"santpSystem-${instanceId}")

  lazy val timeText =   findView[TextView](R.id.txt_time)
  lazy val fracText =   findView[TextView](R.id.txt_frac)
  lazy val offsetText = findView[TextView](R.id.txt_offset)
  lazy val offerrText = findView[TextView](R.id.txt_offerr)
  lazy val offcntText = findView[TextView](R.id.txt_offcnt)
  lazy val offageText = findView[TextView](R.id.txt_offage)
  lazy val geolocText = findView[TextView](R.id.txt_geoloc)
  lazy val ntpText =    findView[TextView](R.id.txt_ntphosts)

  val timeRefs = MutableList[ActorRef]()
  var uiUpdater: ActorRef = null
  var fuser: ActorRef = null
  var ntpRef: ActorRef = null
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

    // FIXME - enable tick display updates
  }

  override def onStop() {
    Log.d(Config.LogName, "Stopping SANTP")

    // FIXME - disable tick display updates

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
    val fracStr = f".${correctedTime % 1000}%03d"
    val age = (sysTime - timeCorrection.last_update) / 1000

    runOnUiThread({
        timeText.setText(timeStr)
        fracText.setText(fracStr)
        offageText.setText(s"${age}s")
    })

    timeCorrection(System.currentTimeMillis)
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
                                       uiUpdater, timeCorrection,
                                       10.0 * 60 * 1000),
                                 "TimeRefFuser")

    val jstrm = getResources().openRawResource(R.raw.ntpzones)
    val ntpmap = NtpZones(jstrm)
    ntpRef = actorSys.actorOf(AkkaProps(classOf[NTPtimeRef],
                                        ntpmap, fuser), "NTPref")
    timeRefs += ntpRef

    val defaultPos = GeoPos(getString(R.string.default_geopos))
    val locMgr = Option(getSystemService(
                              android.content.Context.LOCATION_SERVICE) .
                          asInstanceOf[LocationManager])
    initLocUpdates(locMgr, defaultPos)
    updateLocation(lastKnownLocation, Seq(ntpRef, fuser, uiUpdater))

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

  def displayLocation(pos: GeoPos) {
    val locSummary = f"${pos.latitude}%.2f, ${pos.longitude}%.2f"

    runOnUiThread({
      geolocText.setText(s"Location: ${locSummary}")
    })
  }

  def displayNtpStatus(msg: String) {
    runOnUiThread({
      ntpText.setText(msg)
    })
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

  def onLocationChanged(loc: Location) {
    val sysTime = System.currentTimeMillis
    val gpsTime = loc.getTime()
    val deltaT = (gpsTime - sysTime).toDouble

    loc.getProvider match {
      case LocationManager.GPS_PROVIDER => {
        Log.d(Config.LogName, s"GPS location update (dt=${deltaT}ms)")

        gpsDeltaStats(deltaT)

        fuser ! OffsetModel(deltaT, gpsDeltaStats.stddev)
      }
      case LocationManager.NETWORK_PROVIDER => {
        // FIXME - more here?
      }
      case _ => {
        Log.w(Config.LogName, s"Unused location update from ${loc.getProvider}")
      }
    }

    updateLocation(GeoPos(loc), Seq(ntpRef))
  }
}
