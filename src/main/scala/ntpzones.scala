package uk.rwpenney.santp

import android.util.{JsonReader, Log}
import java.io.{InputStream, InputStreamReader}
import scala.collection.mutable.{HashMap, ListBuffer}


/**
 *  Representation of a geographic region with a collection of NTP hosts
 */
case class Zone(code: String="", ntphosts: List[String]=Nil,
                pos: GeoPos=GeoPos(), radius: Double=1)


/**
 *  A collection of geographic regions with location-specific NTP lookups
 */
class NtpZones(zonedict: Map[String, Zone]) {
  def getHosts(pos: GeoPos, minCount: Int=8) = {
    val distances = (for {
        (ident, zone) <- zonedict
        d = pos.separation(zone.pos) / zone.radius
        nhosts = zone.ntphosts.length
    } yield (d, nhosts, ident)).toList.sorted

    val counts = distances.scanLeft((0, "nowhere")) {
      case ((count, _), (d, nh, id)) => (count + nh, id)
    } . tail

    val tgtCount = counts.find {
      case (count, id) => (count >= minCount)
    } match {
      case Some((count, id)) => count
      case None => minCount
    }
    val nearZones = counts.takeWhile {
      case (count, id) => (count <= tgtCount)
    } map {
      case (count, id) => id
    }

    Log.d(Config.LogName, "Nearest NTP zones: " + nearZones.toString)

    val hosts = (for {
      z <- nearZones
    } yield zonedict(z).ntphosts) . flatten

    (hosts, nearZones)
  }
}


object NtpZones {
  def apply(is: InputStream) = {
    val jreader = new JsonReader(new InputStreamReader(is, "UTF-8"))

    val zones = try {
      readZoneDict(jreader)
    } finally {
      jreader.close()
    }

    new NtpZones(zones)
  }

  def readZoneDict(rdr: JsonReader) = {
    var zones = new HashMap[String, Zone]

    rdr.beginObject()
    while (rdr.hasNext) {
      val zone = rdr.nextName
      val zdef = readZone(rdr)
      zones += (zone -> zdef)
    }
    rdr.endObject()

    zones.toMap
  }

  def readZone(rdr: JsonReader): Zone = {
    var zdef = Zone()

    rdr.beginObject
    while (rdr.hasNext) {
      val field = rdr.nextName
      zdef = field match {
        case "code" =>      zdef.copy(code=rdr.nextString)
        case "latitude" =>  zdef.copy(pos=zdef.pos.copy(
                                              latitude=rdr.nextDouble))
        case "longitude" => zdef.copy(pos=zdef.pos.copy(
                                              longitude=rdr.nextDouble))
        case "ntphosts" =>  zdef.copy(ntphosts=readHosts(rdr))
        case "radius" =>    zdef.copy(radius=rdr.nextDouble)
        case _ => zdef
      }
    }
    rdr.endObject

    zdef
  }

  def readHosts(rdr: JsonReader): List[String] = {
    val hosts = new ListBuffer[String]

    rdr.beginArray
    while (rdr.hasNext) {
      hosts += rdr.nextString
    }
    rdr.endArray

    hosts.toList
  }
}
