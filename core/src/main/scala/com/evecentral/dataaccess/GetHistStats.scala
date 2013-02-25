package com.evecentral.dataaccess

import akka.actor.{Props, Actor}
import com.evecentral.{Database, OrderStatistics}
import org.joda.time.DateTime
import com.google.common.cache.{Cache, CacheBuilder}
import java.util.concurrent.TimeUnit
import akka.routing.SmallestMailboxRouter
import akka.pattern.ask
import com.evecentral.util.ActorNames
import com.evecentral.dataaccess.GetHistStats.CapturedOrderStatistics


object GetHistStats {
	case class Request(marketType: MarketType, bid: Boolean, region: BaseRegion,
                     system: Option[SolarSystem] = None,
                     from: Option[DateTime] = None, to: Option[DateTime] = None)

	case class CapturedOrderStatistics(median: Double, variance: Double, max: Double, avg: Double,
	                                   stdDev: Double, highToLow: Boolean, min: Double, volume: Long,
		                                 fivePercent: Double, wavg: Double, timeat: DateTime) extends OrderStatistics
}

class GetHistStats extends Actor {

	val cache: Cache[GetHistStats.Request, GetHistStats.CapturedOrderStatistics] = CacheBuilder.newBuilder()
  .maximumSize(10000)
  .expireAfterWrite(30, TimeUnit.MINUTES)
  .build()

  private[this] case class StoreStat(stat: CapturedOrderStatistics)

  val dbworker = context.actorOf(Props[GetHistStatsWorker].withRouter(new SmallestMailboxRouter(5)), ActorNames.gethiststats)

	def receive = {
		case req: GetHistStats.Request => {
      Option(cache.getIfPresent(req)) match {
        case None =>
          (dbworker ? req).mapTo[GetHistStats.CapturedOrderStatistics].map { result =>
            cache.put(req, result)
            sender ! result
          }
        case Some(res) =>
          sender ! res
      }
		}
	}
}

class GetHistStatsWorker extends Actor {

	def receive = {
		case GetHistStats.Request(mtype, bid, region, system, from, to) => {
			import net.noerd.prequel.SQLFormatterImplicits._
			val regionid = region match { case AnyRegion() => -1 case Region(id, name) => id }
			val systemid = system match { case Some(s) => s.systemid case None => 0 }
			val bidint = if (bid) 1 else 0
      val fromDate = from.getOrElse(new DateTime().minusDays(1))
      val toDate = to.getOrElse(new DateTime())

			sender ! Database.coreDb.transaction { tx =>
					tx.select("SELECT average,median,volume,stddev,buyup,minimum,maximum,timeat FROM trends_type_region WHERE typeid = ? AND " +
						"systemid = ? AND regionid = ? AND bid = ? AND timeat >= ? AND timeat <= ? ORDER BY timeat",
						mtype.typeid, systemid, regionid, bidint, fromDate, toDate) { row =>
						val avg = row.nextDouble.get
						val median = row.nextDouble.get
						val volume = row.nextLong.get
						val stddev = row.nextDouble.get
						val buyup = row.nextDouble.get
						val minimum = row.nextDouble.get
						val maximum = row.nextDouble.get
						val timeat = new DateTime(row.nextDate.get)
						GetHistStats.CapturedOrderStatistics(median, 0, maximum, avg, stddev, bid, minimum, volume, buyup, 0, timeat)
					}

			}
		}

	}

}