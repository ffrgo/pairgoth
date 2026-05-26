package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.displayRank
import org.jeudego.pairgoth.util.ratingToRank
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object EGFRatingsHandler: RatingsHandler(RatingsManager.Ratings.EGF) {
    val ratingsDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    // pairgoth-hosted mirror of the EGD allworld_lp dump
    override val defaultURL = URL("https://pairgoth.jeudego.org/egd/allworld_lp.zip")
    override fun parsePayload(payload: String): Pair<LocalDate, Json.Array>? {
        val ratingsDateString = payload.lines().firstOrNull { it.startsWith("(") }?.trim()?.removeSurrounding("(", ")")
            ?: run {
                val looksLikeChallenge = payload.contains("Just a moment", ignoreCase = true)
                        || payload.contains("cf-chl", ignoreCase = true)
                        || payload.lineSequence().take(3).any { it.contains("<!DOCTYPE", ignoreCase = true) }
                if (looksLikeChallenge) {
                    logger.warn("EGD download is behind a Cloudflare bot challenge — User-Agent bypass no longer sufficient. Falling back to last cached ratings file. Ask EGD admins to add a Cloudflare page rule excluding /EGD/EGD_2_0/downloads/* from the challenge.")
                } else {
                    logger.warn("EGD response did not contain the expected `(date)` header line. First 200 chars: ${payload.take(200).replace("\n", " ")}")
                }
                return null
            }
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
                        // honor a declared `Np` rank as the pro flag (EGD ground truth);
                        // the strength rank itself is overridden below from the rating.
                        player.getString("rank")?.let { declaredRank ->
                            proSuffixRegex.matchEntire(declaredRank)?.groupValues?.getOrNull(1)
                                ?.toIntOrNull()?.takeIf { it in 1..9 }
                                ?.let { player["pro"] = it }
                        }
                        // override rank with rating equivalent (canonical EGD GoR2Rank mapping);
                        // for pros, keep the `Np` title in the displayed rank.
                        player["rating"]?.toString()?.toIntOrNull()?.let { rating ->
                            val pro = (player["pro"] as? Number)?.toInt() ?: 0
                            player["rank"] = displayRank(ratingToRank(rating), pro)
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
    // EGD disambiguation quirks: last names may carry parentheses, first names trailing digits
    private val nameAtom = "[-._`'a-zA-ZÀ-ÿ()]"
    private val firstnameAtom = "[-._`'a-zA-ZÀ-ÿ0-9]"
    //  19574643  Abad Jahin                            FR  38GJ   20k   --     15     2  T200202B
    var linePattern =
        Regex("\\s+(?<egf>\\d{8})\\s+(?<name>$nameAtom+)\\s(?<firstname>$firstnameAtom+)?,?\\s+(?<country>[A-Z]{2})\\s+(?<club>\\S{1,4})\\s+(?<rank>[1-9][0-9]?[kdp])\\s+(?<promotion>[1-9][0-9]?[kdp]|--)\\s+(?<rating>-?[0-9]+)\\s+(?<nt>[0-9]+)\\s+(?<last>\\S+)\\s*")
    val groups = arrayOf("egf", "name", "firstname", "country", "club", "rank", "rating")
    private val proSuffixRegex = Regex("([1-9])p", RegexOption.IGNORE_CASE)
}
