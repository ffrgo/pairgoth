package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

object EGFRatingsHandler: RatingsHandler(RatingsManager.Ratings.EGF) {
    val ratingsDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    override val defaultURL = URL("https://www.europeangodatabase.eu/EGD/EGD_2_0/downloads/allworld_lp.html")
    override fun parsePayload(payload: String): Pair<LocalDate, Json.Array> {
        val ratingsDateString = payload.lines().filter { it.startsWith("(") }.first().trim().removeSurrounding("(", ")")
        val ratingsDate = LocalDate.parse(ratingsDateString, ratingsDateFormatter)
        return Pair(
            ratingsDate,
            payload.lines().filter {
                it.matches(Regex("\\s+\\d+(?!.*\\(undefined\\)|Anonymous).*"))
            }.mapNotNullTo(Json.MutableArray()) {
                val match = linePattern.matchEntire(it)
                if (match == null) {
                    logger.debug("could not parse line: $it")
                    null
                } else {
                    val pairs = groups.map {
                        Pair(it, match.groups[it]?.value)
                    }.toTypedArray()
                    Json.MutableObject(*pairs).also { player ->
                        player["origin"] = "EGF"
                        // override rank with rating equivalent
                        player["rating"]?.toString()?.toIntOrNull()?.let { rating ->
                            val adjusted = rating - 2050;
                            player["rank"] =
                                if (adjusted < 0) "${-(adjusted - 99) / 100}k"
                                else "${(adjusted + 100) / 100}d"
                        }
                        if ("UK" == player.getString("country")) {
                            player["country"] = "GB"
                        }
                        // fix for missing firstnames
                        if (player.getString("firstname") == null) {
                            player["firstname"] = ""
                        }
                    }
                }
            }
        )
    }
    //  19574643  Abad Jahin                            FR  38GJ   20k   --     15     2  T200202B
    var linePattern =
        Regex("\\s+(?<egf>\\d{8})\\s+(?<name>$atom+)\\s(?<firstname>$atom+)?,?\\s+(?<country>[A-Z]{2})\\s+(?<club>\\S{1,4})\\s+(?<rank>[1-9][0-9]?[kdp])\\s+(?<promotion>[1-9][0-9]?[kdp]|--)\\s+(?<rating>-?[0-9]+)\\s+(?<nt>[0-9]+)\\s+(?<last>\\S+)\\s*")
    val groups = arrayOf("egf", "name", "firstname", "country", "club", "rank", "rating")
}
