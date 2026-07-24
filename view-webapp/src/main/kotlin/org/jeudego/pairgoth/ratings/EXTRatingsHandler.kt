package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.WebappManager
import java.net.URL
import java.time.LocalDate

// External registry source: a tournament website's roster served as JSON
// `{"date": "YYYY-MM-DD", "players": [{name, firstname, country, club, rank, rating, ext}, ...]}`.
// Inactive unless a roster URL is configured.
object EXTRatingsHandler: RatingsHandler(RatingsManager.Ratings.EXT) {
    override val active get() = WebappManager.properties.getProperty("ratings.ext") != null
    // participant registry, not a ratings snapshot: late registrants must not be frozen out
    override val freezable = false
    override val defaultURL: URL
        get() = throw Error("no default URL for EXT: set the ratings.ext property")
    override fun parsePayload(payload: String): Pair<LocalDate, Json.Array>? {
        val roster = try {
            Json.parse(payload)?.asObject()
        } catch (e: Exception) {
            null
        } ?: run {
            logger.warn("EXT roster is not a JSON object. First 200 chars: ${payload.take(200).replace("\n", " ")}")
            return null
        }
        val date = roster.getString("date")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: run {
            logger.warn("EXT roster has no parseable `date` field")
            return null
        }
        val players = roster.getArray("players") ?: run {
            logger.warn("EXT roster has no `players` array")
            return null
        }
        return Pair(
            date,
            players.mapNotNullTo(Json.MutableArray()) { p ->
                (p as? Json.MutableObject)?.also { it["origin"] = "EXT" }
            }
        )
    }
}
