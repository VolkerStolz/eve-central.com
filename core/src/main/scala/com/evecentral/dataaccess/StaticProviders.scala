package com.evecentral.dataaccess

import com.evecentral.Database

object StationNameUtility {
  def shorten(name: String) : String = {

    val split = "Moon ".r.replaceAllIn(name, "M").split(" - ")
    val head = split.reverse.tail.reverse.mkString(" - ")
    val words = split.last.split(" ").map(s => s.charAt(0)).mkString
    head + " - " + words
  }
}


object QueryDefaults {
  val minQLarge: Long = 10001
  lazy val minQExceptions = List[Long](34, 35, 36, 37, 38, 39, 40, 11399).foldLeft(Map[Long, Long]()) {
    (i, s) => i ++ Map(s -> minQLarge)
  }

  /**
   * Determine a sane minimum quantity. For minerals, this is usually set higher than
   * other values. The minQExceptions list maps the quantities.
   */
  def minQ(typeid: Long): Long = {
    minQExceptions.getOrElse(typeid, 1)
  }
}

case class Region(regionid: Long, name: String)

case class Station(stationid: Long, name: String, shortName: String, system: SolarSystem)

case class SolarSystem(systemid: Long, name: String, security: Double, region: Region, constellationid: Long)

case class MarketType(typeid: Long,  name: String)

/**
 * A provider of static data which has all be loaded into memory.
 */
object StaticProvider {

  /**
   *  A list of regions considered to be in Empire Space
   */
  lazy val empireRegions = List[Long](10000001, 10000002, 10000016, 10000020, 10000028, 10000030, 10000032, 10000033,
    10000043, 10000049, 10000037, 10000038, 10000036, 10000052, 10000064, 10000065, 10000067,
    10000068, 10000054, 10000042, 10000044, 10000048).map(id => regionsMap(id))

  
  /**
   * Maps a systemId to a solarsystem.
   */
  lazy val systemsMap = {
    var m = Map[Long, SolarSystem]()
    Database.coreDb.transaction {
      tx =>
        tx.selectAndProcess("SELECT systemid,systemname,security,regionid,constellationid FROM systems") {
          row =>
            val sysid = row.nextLong.get
            val name = row.nextString.get
            val security = row.nextDouble.get
            val regionid = row.nextLong.get
            val constellationid = row.nextLong.get
            
            m = m ++ Map(sysid -> SolarSystem(sysid, name, security, regionsMap(regionid), constellationid))
        }
    }
    m
  }

  /**
   * Maps a station ID to a station
   */
  lazy val stationsMap = {
    var m = Map[Long, Station]()
    Database.coreDb.transaction {
      tx =>
        tx.selectAndProcess("SELECT stationid,stationname,systemid FROM stations") {
          row =>
            val staid = row.nextLong.get
            val name = row.nextString.get
            val sysid = row.nextLong.get
            val shortName = StationNameUtility.shorten(name)
            m = m ++ Map(staid -> Station(staid, name, shortName, systemsMap(sysid)))
        }
    }
    m
  }

  /**
   * Maps a region ID to a Region
   */
  lazy val regionsMap = {
    var m = Map[Long, Region]()
    Database.coreDb.transaction {
      tx =>
        tx.selectAndProcess("SELECT regionid,regionname FROM regions") {
          row =>
            val sysid = row.nextLong.get
            val name = row.nextString.get

            m = m ++ Map(sysid -> Region(sysid, name))
        }
    }
    m
  }

  lazy val typesMap = {
    var m = Map[Long, MarketType]()
    Database.coreDb.transaction {
      tx =>
        tx.selectAndProcess("SELECT typeid,typename FROM types") {
          row =>
            val sysid = row.nextLong.get
            val name = row.nextString.get
            m = m ++ Map(sysid -> MarketType(sysid, name))
        }
    }
    m
  }

}
