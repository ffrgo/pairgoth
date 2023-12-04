package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*

object RatingsManager: Runnable {

    enum class Ratings {
        AGA,
        EGF,
        FFG
    }

    val ratingsHandlers by lazy {
        mapOf(
            Pair(Ratings.AGA, AGARatingsHandler),
            Pair(Ratings.EGF, EGFRatingsHandler),
            Pair(Ratings.FFG, FFGRatingsHandler)
        );
    }

    val timer = Timer()
    lateinit var players: Json.MutableArray
    override fun run() {
        logger.info("launching ratings manager")
        timer.scheduleAtFixedRate(Task, 0L, 3600000L)
    }
    object Task: TimerTask() {
        override fun run() {
            players = ratingsHandlers.values.filter { it.active }.flatMapTo(Json.MutableArray()) { ratings ->
                ratings.fetchPlayers()
            }
        }
    }
    val logger = LoggerFactory.getLogger("ratings")
    val path = Path.of(WebappManager.getProperty("ratings.path") ?: "ratings").also {
        val file = it.toFile()
        if (!file.mkdirs() && !file.isDirectory) throw Error("Property pairgoth.ratings.path must be a directory")
    }
}
