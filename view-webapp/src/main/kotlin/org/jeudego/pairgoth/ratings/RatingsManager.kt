package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.getResourceAsStream
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.lang.Exception
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

object RatingsManager: Runnable {

    enum class Ratings(val flag: Int) {
        AGA(1),
        EGF(2),
        FFG(4);
        companion object {
            fun valueOf(mask: Int): Ratings {
                if (mask.countOneBits() != 1) throw Error("wrong use")
                return values().filter { it.flag == mask }.firstOrNull() ?: throw Error("wrong mask")
            }
            fun codeOf(mask: Int) = valueOf(mask).name.lowercase(Locale.ROOT)
        }
    }

    val ratingsHandlers by lazy {
        mapOf(
            Pair(Ratings.AGA, AGARatingsHandler),
            Pair(Ratings.EGF, EGFRatingsHandler),
            Pair(Ratings.FFG, FFGRatingsHandler)
        );
    }

    fun activeMask() = ratingsHandlers.entries.filter { it.value.active }.map { it.key.flag }.reduce { a,b -> a or b }

    val timer = Timer()
    lateinit var players: Json.MutableArray
    val updateLock: ReadWriteLock = ReentrantReadWriteLock()
    val egf2ffg by lazy {
        BufferedReader(getResourceAsStream("/egf2ffg.csv").reader()).readLines().associate {
            it.split(',').zipWithNext().first()
        }
    }

    override fun run() {
        logger.info("launching ratings manager")
        timer.scheduleAtFixedRate(Task, 0L, 3600000L)
    }
    object Task: TimerTask() {
        override fun run() {
            try {
                players = ratingsHandlers.values.filter { it.active }.flatMapTo(Json.MutableArray()) { ratings ->
                    ratings.fetchPlayers()
                }
                val updated = ratingsHandlers.values.filter { it.active }.map { it.updated() }.reduce { u1, u2 ->
                    u1 or u2
                }
                if (updated) {
                    try {
                        updateLock.writeLock().lock()
                        index.build(players)
                    } finally {
                        updateLock.writeLock().unlock()
                    }
                }

                // propagate French players license status from ffg to egf
                val licenseStatus = players.map { it -> it as Json.MutableObject }.filter {
                    it["origin"] == "FFG"
                }.associate { player ->
                    Pair(player.getString("ffg")!!, player.getString("license") ?: "-")
                }
                players.map { it -> it as Json.MutableObject }.filter {
                    it["origin"] == "EGF" && it["country"] == "FR"
                }.forEach { player ->
                    player.getString("egf")?.let { egf ->
                        egf2ffg[egf]?.let { ffg ->
                            licenseStatus[ffg]?.let {
                                player["license"] = it
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                logger.error("could not build or refresh index", e)
            }
        }
    }
    val logger = LoggerFactory.getLogger("ratings")
    val path = Path.of(WebappManager.properties.getProperty("ratings.path") ?: "ratings").also {
        val file = it.toFile()
        if (!file.mkdirs() && !file.isDirectory) throw Error("Property pairgoth.ratings.path must be a directory")
    }

    fun search(needle: String, aga: Boolean, egf: Boolean, ffg: Boolean, country: String?): Json.Array {
        try {
            updateLock.readLock().lock()
            var mask = 0
            if (aga && ratingsHandlers[Ratings.AGA]!!.active) mask = mask or Ratings.AGA.flag
            if (egf && ratingsHandlers[Ratings.EGF]!!.active) mask = mask or Ratings.EGF.flag
            if (ffg && ratingsHandlers[Ratings.FFG]!!.active) mask = mask or Ratings.FFG.flag
            return if (needle == "*") {
                sortedPlayers(mask, country)
            } else {
                val matches = index.match(needle, mask, country)
                matches.map { it -> players[it] }.toCollection(Json.MutableArray())
            }
        } finally {
            updateLock.readLock().unlock()
        }

    }

    private fun sortedPlayers(mask: Int, country: String?): Json.Array {
        val cntry = country?.let { it.uppercase(Locale.ROOT) }
        val orig = Ratings.values().filter { (it.flag and mask) != 0 }.map { it.name }.toSet()
        return players.filter {
            val player = it as Json.Object
            (cntry == null || cntry == player.getString("country")) && orig.contains(player.getString("origin"))
        }.sortedWith { a,b ->
            val left = a as Json.Object
            val right = b as Json.Object
            val cmp = left.getString("name")!!.compareTo(right.getString("name")!!)
            if (cmp == 0) left.getString("firstname")!!.compareTo(right.getString("firstname")!!)
            else cmp
        }.toCollection(Json.MutableArray())
    }

    val index = PlayerIndex()
}
