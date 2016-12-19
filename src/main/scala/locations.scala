/*
 *  Geographic location classes for Scala/Android NTP app
 *  RW Penney, January 2016
 */
package uk.rwpenney.santp

import akka.actor.{ Actor, ActorRef }
import android.location.{Location, LocationListener, LocationManager}
import android.util.Log


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

object GeoPos {
  /// Create GeoPos from lat,lng string
  def apply(s: String) = {
    val latlng = s.split(",").map(_.toFloat)

    new GeoPos(latlng(0), latlng(1))
  }

  def apply(loc: Location) = new GeoPos(loc.getLatitude,
                                        loc.getLongitude)
}


/**
 *  Mixin to assist with converting GPS location updates
 *  into clock-offset measurements
 *
 *  See [[TimeRef]].
 */
trait GeoLocator extends LocationListener {
  val gpsDeltaStats = new ExpoAverager(0, 200, 0.2)
  val errorGrowthRate = 0.01
  var lastKnownLocation = GeoPos(90, -180)
  var lastAdvertisedLocation = GeoPos(-90, 270)

  def initLocUpdates(lmopt: Option[LocationManager], origin: GeoPos) {
    lmopt match {
      case Some(lm) => {
        Log.i(Config.LogName, "Subscribing to location-updates")

        requestGPSupdates(lm)

        try {
          requestCrudeUpdates(lm)
        } catch {
          case ex: IllegalArgumentException =>  // mostly benign
        }

        lastKnownLocation = estimateLocation(lm)
      }
      case None => {
        Log.w(Config.LogName, s"GeoLocator using default location ${origin}")

        lastKnownLocation = origin
      }
    }
  }

  def requestGPSupdates(lm: LocationManager, interval_s: Int=19) {
    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                              interval_s * 1000, 0.0f, this)
  }

  def requestCrudeUpdates(lm: LocationManager, interval_mins: Int=5) {
    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                              interval_mins * 60 * 1000, 20e3f, this)
  }

  def estimateLocation(lm: LocationManager): GeoPos = {
    import scala.collection.JavaConverters._

    val providers = lm.getProviders(false).asScala
    val now = System.currentTimeMillis

    val locs = providers.flatMap {
                p => Option(lm.getLastKnownLocation(p))
               } . sortBy(loc => (loc.getAccuracy
                                      + (now - loc.getTime) * errorGrowthRate))

    if (locs.length >= 1) {
      val loc = locs.head
      Log.i(Config.LogName,
            s"Best location provider of ${locs.length}: ${loc.getProvider}")
      GeoPos(loc.getLatitude, loc.getLongitude)
    } else{
      Log.w(Config.LogName, s"No usable location providers")
      lastKnownLocation
    }
  }

  def updateLocation(newpos: GeoPos, clients: Seq[ActorRef]) {
    lastKnownLocation = newpos

    // Crudely estimate distance moved since last message:
    val GeoPos(oldlat, oldlng) = lastAdvertisedLocation
    val angshift = (newpos.longitude - oldlng + 3 * 360) % 360.0
                    + (newpos.latitude - oldlat + 3 * 180) % 180.0

    if (angshift > 0.3) {
      Log.d(Config.LogName, s"Advertising location change (${angshift})")

      clients.foreach { client =>
        client ! newpos
      }

      lastAdvertisedLocation = lastKnownLocation
    }
  }

  def onProviderDisabled(p: String) {}
  def onProviderEnabled(p: String) {}
  def onStatusChanged(p: String, status: Int, extras: android.os.Bundle) {}
}
