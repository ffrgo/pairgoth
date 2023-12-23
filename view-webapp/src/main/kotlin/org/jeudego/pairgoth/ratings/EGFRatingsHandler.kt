package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import java.net.URL

object EGFRatingsHandler: RatingsHandler(RatingsManager.Ratings.EGF) {
    override val defaultURL = URL("https://www.europeangodatabase.eu/EGD/EGD_2_0/downloads/allworld_lp.html")
    override fun parsePayload(payload: String): Json.Array {
        return payload.lines().filter {
            it.matches(Regex("\\s+\\d+(?!.*\\(undefined\\)|Anonymous).*"))
        }.mapNotNullTo(Json.MutableArray()) {
            val match = linePattern.matchEntire(it)
            if (match == null) {
                logger.error("could not parse line: $it")
                null
            } else {
                val pairs = groups.map {
                    Pair(it, match.groups[it]?.value)
                }.toTypedArray()
                Json.MutableObject(*pairs).also {
                    it["origin"] = "EGF"
                }
            }
        }
    }
    //  19574643  Abad Jahin                            FR  38GJ   20k   --     15     2  T200202B
    var linePattern =
        Regex("\\s+(?<egf>\\d{8})\\s+(?<name>$atom+)\\s(?<firstname>$atom+)?,?\\s+(?<country>[A-Z]{2})\\s+(?<club>\\S{1,4})\\s+(?<rank>[1-9][0-9]?[kdp])\\s+(?<promotion>[1-9][0-9]?[kdp]|--)\\s+(?<rating>-?[0-9]+)\\s+(?<nt>[0-9]+)\\s+(?<last>\\S+)\\s*")
    val groups = arrayOf("egf", "name", "firstname", "country", "club", "rank", "rating")
}
