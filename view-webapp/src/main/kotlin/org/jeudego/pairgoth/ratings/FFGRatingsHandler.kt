package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.displayRank
import org.jeudego.pairgoth.util.ratingToRank
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FFGRatingsHandler: RatingsHandler(RatingsManager.Ratings.FFG) {
    val ratingsDateFormatter = DateTimeFormatter.ofPattern("d/MM/yyyy")
    override val defaultURL = URL("https://ffg.jeudego.org/echelle/echtxt/ech_ffg_V3.txt")
    override fun parsePayload(payload: String): Pair<LocalDate, Json.Array>? {
        val firstLine = payload.lineSequence().firstOrNull()
        if (firstLine == null || !firstLine.contains("#Echelle au ")) {
            logger.warn("FFG response did not contain the expected `#Echelle au <date>` header. First 200 chars: ${payload.take(200).replace("\n", " ")}")
            return null
        }
        val ratingsDateString = firstLine.substringAfter("#Echelle au ").substringBefore(" ")
        val ratingsDate = LocalDate.parse(ratingsDateString, ratingsDateFormatter)
        return Pair(
              ratingsDate,
              payload.lines().mapNotNullTo(Json.MutableArray()) { line ->
                val match = linePattern.matchEntire(line)
                if (match == null) {
                    logger.debug("could not parse line: $line")
                    null
                } else {
                    val pairs = groups.map {
                        Pair(it, match.groups[it]?.value)
                    }.toTypedArray()
                    Json.MutableObject(*pairs).also {
                        it["origin"] = "FFG"
                        // FFG echelle does not carry pro grades; mirror the affichePersonne.php
                        // hardcoded list (FFG dev repo) for French pros, keyed by license.
                        val pro = frenchPros[it.getString("ffg")] ?: 0
                        if (pro > 0) it["pro"] = pro
                        val rating = it["rating"]?.toString()?.toIntOrNull()
                        if (rating != null) {
                            // shift FFG raw rating to EGD GoR scale (FFG 0 ≈ EGD 1k/1d boundary at 2050)
                            val gor = rating + 2050
                            it["rating"] = gor
                            it["rank"] = displayRank(ratingToRank(gor), pro)
                        }
                    }
                }
            }
        )
    }

    override fun defaultCharset() = StandardCharsets.ISO_8859_1

    var linePattern =
        Regex("(?<name>$atom+(?:\\((?:$atom|[0-9])+\\))?)\\s(?<firstname>$atom+(?:\\((?:$atom|[0-9])+\\))?)\\s+(?<rating>-?[0-9]+)\\s(?<license>[-eCLX])\\s(?<ffg>(?:\\d|[A-Z]){7}|-------)\\s(?<club>xxxx|XXXX|\\d{2}[a-zA-Z0-9]{2})\\s(?<country>[A-Z]{2})")
    val groups = arrayOf("name", "firstname", "rating", "license", "ffg", "club", "country")

    // French pros licensed in France; mirrors ffg.jeudego.org/php/affichePersonne.php $joueurs_pro.
    // Foreign pros (Fan Hui, Catalin Taranu) are not licensed here and stay out.
    private val frenchPros = mapOf(
        "0635000" to 1,  // Benjamin Dréan-Guénaïzia (1p, EGF)
        "0700460" to 1,  // Tanguy Le Calvé (1p, EGF)
    )
}
