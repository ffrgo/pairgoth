package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object FFGRatingsHandler: RatingsHandler(RatingsManager.Ratings.FFG) {
    override val defaultURL = URL("https://ffg.jeudego.org/echelle/echtxt/ech_ffg_V3.txt")
    override fun parsePayload(payload: String): Json.Array {
        return payload.lines().mapNotNullTo(Json.MutableArray()) { line ->
            val match = linePattern.matchEntire(line)
            if (match == null) {
                logger.error("could not parse line: $line")
                null
            } else {
                val pairs = groups.map {
                    Pair(it, match.groups[it]?.value)
                }.toTypedArray()
                Json.MutableObject(*pairs).also {
                    it["origin"] = "FFG"
                    val rating = it["rating"]?.toString()?.toIntOrNull()
                    if (rating != null) {
                        it["rank"] = (rating/100).let { if (it < 0) "${-it}k" else "${it+1}d" }
                    }
                }
            }
        }
    }

    override fun defaultCharset() = StandardCharsets.ISO_8859_1

    var linePattern =
        Regex("(?<name>$atom+(?:\\((?:$atom|[0-9])+\\))?)\\s(?<firstname>$atom+(?:\\((?:$atom|[0-9])+\\))?)\\s+(?<rating>-?[0-9]+)\\s(?<license>[-eCLX])\\s(?<ffg>(?:\\d|[A-Z]){7}|-------)\\s(?<club>xxxx|XXXX|\\d{2}[a-zA-Z0-9]{2})\\s(?<country>[A-Z]{2})")
    val groups = arrayOf("name", "firstname", "rating", "license", "club", "country")
}
