package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.util.MAX_PRO as COMMON_MAX_PRO
import org.jeudego.pairgoth.util.MAX_RANK as COMMON_MAX_RANK
import org.jeudego.pairgoth.util.MIN_PRO as COMMON_MIN_PRO
import org.jeudego.pairgoth.util.MIN_RANK as COMMON_MIN_RANK
import org.jeudego.pairgoth.util.proToRating as commonProToRating
import org.jeudego.pairgoth.util.ratingToPro as commonRatingToPro
import org.jeudego.pairgoth.util.ratingToRank as commonRatingToRank
import org.jeudego.pairgoth.util.rankToRating as commonRankToRating
import java.util.*

// Canonical internal form for player surnames and first names: Title_Case-per-word,
// words joined by single space. Hyphen and apostrophe are kept in place and treated as
// case-boundaries. Underscore is treated as a legacy word-separator and folded to space,
// so previously stored "Lebas_de_Saint_Martin" auto-migrates to "Lebas De Saint Martin"
// on next read. Diacritics preserved (UTF-8 internal). Idempotent in steady state.
// Export paths re-split on whitespace and rejoin with '_' (FFG/EGF require no spaces).
fun String.toCanonicalName(): String {
    val normalized = trim().replace(Regex("(?:\\s|\\xA0|_)+"), " ")
    val sb = StringBuilder(normalized.length)
    var capitalizeNext = true
    for (c in normalized) {
        if (c.isWhitespace() || c == '-' || c == '\'') {
            sb.append(c)
            capitalizeNext = true
        } else if (capitalizeNext) {
            sb.append(c.titlecase(Locale.ROOT))
            capitalizeNext = false
        } else {
            sb.append(c.lowercase(Locale.ROOT))
        }
    }
    return sb.toString()
}

// Pairable

sealed class Pairable(val id: ID, val name: String, val rating: Int, val rank: Int, val final: Boolean, val mmsCorrection: Int = 0) {
    // `rank` is the HONORARY grade: a stored label that the organiser may decouple from rating
    // via the link/unlink UI. Pairing must NEVER read it. Everything that decides who plays whom
    // — MMS groups (mmBase), handicap, seeding and sorting — reads `effectiveRank` instead, which
    // is always derived from rating. This is what makes unlink/link harmless.
    val effectiveRank: Int get() = commonRatingToRank(rating)
    companion object {
        val MIN_RANK: Int = COMMON_MIN_RANK
        val MAX_RANK: Int = COMMON_MAX_RANK
        val MIN_PRO: Int = COMMON_MIN_PRO
        val MAX_PRO: Int = COMMON_MAX_PRO

        fun ratingToRank(rating: Int): Int = commonRatingToRank(rating)
        fun rankToRating(rank: Int): Int = commonRankToRating(rank)
        fun ratingToPro(rating: Int): Int = commonRatingToPro(rating)
        fun proToRating(pro: Int): Int = commonProToRating(pro)
    }
    fun toJson(): Json.Object = toMutableJson()
    abstract fun toMutableJson(): Json.MutableObject
    open fun toDetailedJson() = toMutableJson()
    abstract val club: String?
    abstract val country: String?
    open fun fullName(separator: String = " "): String {
        return name
    }
    open val skip = mutableSetOf<Int>() // skipped rounds

    fun equals(other: Pairable): Boolean {
        return id == other.id
    }

    open fun canPlay(round: Int) = !skip.contains(round)
}

object ByePlayer: Pairable(0, "bye", 0, Int.MIN_VALUE, true) {
    override fun toMutableJson(): Json.MutableObject {
        throw Error("bye player should never be serialized")
    }

    override val club = "none"
    override val country = "none"
}

// re-exported from pairgoth-common util for callers that import org.jeudego.pairgoth.model.*
fun displayRank(rank: Int) = org.jeudego.pairgoth.util.displayRank(rank)
fun displayRank(rank: Int, pro: Int) = org.jeudego.pairgoth.util.displayRank(rank, pro)
// Honorary grade display: stored rank + pro title.
fun Pairable.displayRank() = when (val p = (this as? Player)?.pro ?: 0) {
    in Pairable.MIN_PRO..Pairable.MAX_PRO -> "${p}p"
    else -> displayRank(rank)
}
// Effective pairing rank display (rating-derived), plain k/d — never a pro title.
fun Pairable.displayEffectiveRank() = displayRank(effectiveRank)

private val rankRegex = Regex("(\\d+)([kdp])", RegexOption.IGNORE_CASE)

// Returns null on invalid input. For `p` (pro) inputs, returns null too — pro requires
// the (rank, pro) pair, see [parseRankAndPro]. Amateur input returns the rank int.
// Caller decides the fallback (typically -20 / 20k with a warning at import boundaries).
fun Pairable.Companion.parseRank(rankStr: String): Int? = parseRankAndPro(rankStr)?.let {
    if (it.second == 0) it.first else null
}

// Returns (rank, pro) where rank is amateur-equivalent strength (-30..MAX_RANK) and
// pro is 0 for amateur or 1..9 for pro grades. For `Np` input, rank is derived from
// the canonical pro→rating→rank mapping (so pairing/MMS treats pros at their rating
// equivalent). Returns null on garbage.
fun Pairable.Companion.parseRankAndPro(rankStr: String): Pair<Int, Int>? {
    val (level, letter) = rankRegex.matchEntire(rankStr.trim())?.destructured ?: return null
    val num = level.toIntOrNull() ?: return null
    if (num < 1) return null
    return when (letter.lowercase()) {
        "k" -> if (num > -MIN_RANK) null else Pair(-num, 0)              // 1k..30k
        "d" -> if (num > MAX_RANK + 1) null else Pair(num - 1, 0)        // 1d..9d
        "p" -> if (num !in MIN_PRO..MAX_PRO) null
               else Pair(ratingToRank(proToRating(num)), num)            // 1p..9p
        else -> null
    }
}

// Player

enum class DatabaseId {
    AGA,
    EGF,
    FFG,
    // External registration source (e.g. tournament website pushing players via webhook)
    EXT;
    val key get() = this.name.lowercase(Locale.ROOT)
}

class Player(
    id: ID,
    name: String,
    var firstname: String,
    rating: Int,
    rank: Int,
    override var country: String,
    override var club: String,
    final: Boolean,
    mmsCorrection: Int = 0,
    // Professional grade: 0 = amateur (default), 1..9 = pro (1p..9p). Orthogonal to
    // rank, which always carries the amateur-equivalent strength used by pairing/MMS.
    var pro: Int = 0
): Pairable(id, name, rating, rank, final, mmsCorrection) {
    companion object
    // used to store external IDs ("FFG" => FFG ID, "EGF" => EGF PIN, "AGA" => AGA ID ...)
    val externalIds = mutableMapOf<DatabaseId, String>()
    override fun toMutableJson() = Json.MutableObject(
        "id" to id,
        "name" to name,
        "firstname" to firstname,
        "rating" to rating,
        "rank" to rank,
        "country" to country,
        "club" to club,
        "final" to final
    ).also { json ->
        if (skip.isNotEmpty()) json["skip"] = Json.Array(skip)
        if (mmsCorrection != 0) json["mmsCorrection"] = mmsCorrection
        if (pro != 0) json["pro"] = pro
        externalIds.forEach { (dbid, id) ->
            json[dbid.key] = id
        }
    }

    override fun fullName(separator: String): String {
        return name + separator + firstname
    }
}

// canonicalize=false skips name normalisation; use for disk-restore paths where the
// stored form must round-trip unchanged (otherwise detRandom-driven pairings would shift
// when a tournament is reopened — names feed detRandom in pairing/Random.kt).
fun Player.Companion.fromJson(json: Json.Object, default: Player? = null, canonicalize: Boolean = true) = Player(
    id = json.getInt("id") ?: default?.id ?: nextPlayerId,
    name = (json.getString("name") ?: default?.name ?: badRequest("missing name")).let { if (canonicalize) it.toCanonicalName() else it },
    firstname = (json.getString("firstname") ?: default?.firstname ?: badRequest("missing firstname")).let { if (canonicalize) it.toCanonicalName() else it },
    rating = json.getInt("rating") ?: default?.rating ?: badRequest("missing rating"),
    rank = json.getInt("rank") ?: default?.rank ?: badRequest("missing rank"),
    country = ( json.getString("country") ?: default?.country ?: badRequest("missing country") ).let {
        // normalize to UK (EGF uses UK, ISO uses GB)
        val up = it.uppercase(Locale.ROOT)
        if (up == "GB") "UK" else up
    },
    club = json.getString("club") ?: default?.club ?: badRequest("missing club"),
    final = json.getBoolean("final") ?: default?.final ?: true,
    mmsCorrection = json.getInt("mmsCorrection") ?: default?.mmsCorrection ?: 0,
    pro = (json.getInt("pro") ?: default?.pro ?: 0).let {
        if (it != 0 && it !in Pairable.MIN_PRO..Pairable.MAX_PRO) badRequest("invalid pro grade: $it")
        it
    }
).also { player ->
    (json.getArray("skip") ?: default?.skip)?.let {
        if (it.isNotEmpty()) player.skip.addAll(it.map { id -> (id as Number).toInt() })
    }
    DatabaseId.values().forEach { dbid ->
        (json.getString(dbid.key) ?: default?.externalIds?.get(dbid))?.let { id ->
            player.externalIds[dbid] = id
        }
    }
}
