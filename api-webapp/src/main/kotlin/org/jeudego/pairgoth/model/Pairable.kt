package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.util.MAX_RANK as COMMON_MAX_RANK
import org.jeudego.pairgoth.util.MIN_RANK as COMMON_MIN_RANK
import org.jeudego.pairgoth.util.ratingToRank as commonRatingToRank
import org.jeudego.pairgoth.util.rankToRating as commonRankToRating
import java.util.*

// Pairable

sealed class Pairable(val id: ID, val name: String, val rating: Int, val rank: Int, val final: Boolean, val mmsCorrection: Int = 0) {
    companion object {
        val MIN_RANK: Int = COMMON_MIN_RANK
        val MAX_RANK: Int = COMMON_MAX_RANK

        fun ratingToRank(rating: Int): Int = commonRatingToRank(rating)
        fun rankToRating(rank: Int): Int = commonRankToRating(rank)
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
fun Pairable.displayRank() = displayRank(rank)

private val rankRegex = Regex("(\\d+)([kdp])", RegexOption.IGNORE_CASE)

// Returns null on invalid input or on `p` ranks (pro support comes in a later step).
// Caller decides the fallback (typically -20 / 20k with a warning at import boundaries).
fun Pairable.Companion.parseRank(rankStr: String): Int? {
    val (level, letter) = rankRegex.matchEntire(rankStr.trim())?.destructured ?: return null
    val num = level.toIntOrNull() ?: return null
    if (num < 1) return null
    return when (letter.lowercase()) {
        "k" -> if (num > -MIN_RANK) null else -num     // 1k..30k
        "d" -> if (num > MAX_RANK + 1) null else num - 1 // 1d..9d
        "p" -> null // pro: not yet represented in the rank-only Int domain
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
    mmsCorrection: Int = 0
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
        externalIds.forEach { (dbid, id) ->
            json[dbid.key] = id
        }
    }

    override fun fullName(separator: String): String {
        return name + separator + firstname
    }
}

fun Player.Companion.fromJson(json: Json.Object, default: Player? = null) = Player(
    id = json.getInt("id") ?: default?.id ?: nextPlayerId,
    name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
    firstname = json.getString("firstname") ?: default?.firstname ?: badRequest("missing firstname"),
    rating = json.getInt("rating") ?: default?.rating ?: badRequest("missing rating"),
    rank = json.getInt("rank") ?: default?.rank ?: badRequest("missing rank"),
    country = ( json.getString("country") ?: default?.country ?: badRequest("missing country") ).let {
        // normalize to UK (EGF uses UK, ISO uses GB)
        val up = it.uppercase(Locale.ROOT)
        if (up == "GB") "UK" else up
    },
    club = json.getString("club") ?: default?.club ?: badRequest("missing club"),
    final = json.getBoolean("final") ?: default?.final ?: true,
    mmsCorrection = json.getInt("mmsCorrection") ?: default?.mmsCorrection ?: 0
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
