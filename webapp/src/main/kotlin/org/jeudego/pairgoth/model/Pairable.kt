package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import kotlin.math.roundToInt

// Pairable

sealed class Pairable(val id: Int, val name: String, open val rating: Int, open val rank: Int) {
    companion object {}
    abstract fun toJson(): Json.Object
    abstract val club: String?
    abstract val country: String?
    val skip = mutableSetOf<Int>() // skipped rounds
}

object ByePlayer: Pairable(0, "bye", 0, Int.MIN_VALUE) {
    override fun toJson(): Json.Object {
        throw Error("bye player should never be serialized")
    }

    override val club = "none"
    override val country = "none"
}

fun Pairable.displayRank(): String = when {
    rank < 0 -> "${-rank}k"
    rank < 10 -> "${rank + 1}d"
    else -> "${rank - 9}p"
}

private val rankRegex = Regex("(\\d+)([kdp])", RegexOption.IGNORE_CASE)

fun Pairable.Companion.parseRank(rankStr: String): Int {
    val (level, letter) = rankRegex.matchEntire(rankStr)?.destructured ?: throw Error("invalid rank: $rankStr")
    val num = level.toInt()
    if (num < 0 || letter != "k" && letter != "K" && num > 9) throw Error("invalid rank: $rankStr")
    return when (letter.lowercase()) {
        "k" -> -num
        "d" -> num - 1
        "p" -> num + 9
        else -> throw Error("impossible")
    }
}

// Player

class Player(
    id: Int,
    name: String,
    var firstname: String,
    rating: Int,
    rank: Int,
    override var country: String,
    override var club: String
): Pairable(id, name, rating, rank) {
    companion object
    // used to store external IDs ("FFG" => FFG ID, "EGF" => EGF PIN, "AGA" => AGA ID ...)
    val externalIds = mutableMapOf<String, String>()
    override fun toJson(): Json.Object = Json.MutableObject(
        "id" to id,
        "name" to name,
        "firstname" to firstname,
        "rating" to rating,
        "rank" to rank,
        "country" to country,
        "club" to club
    ).also {
        if (skip.isNotEmpty()) it["skip"] = Json.Array(skip)
    }
}

fun Player.Companion.fromJson(json: Json.Object, default: Player? = null) = Player(
    id = json.getInt("id") ?: default?.id ?: Store.nextPlayerId,
    name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
    firstname = json.getString("firstname") ?: default?.firstname ?: badRequest("missing firstname"),
    rating = json.getInt("rating") ?: default?.rating ?: badRequest("missing rating"),
    rank = json.getInt("rank") ?: default?.rank ?: badRequest("missing rank"),
    country = json.getString("country") ?: default?.country ?: badRequest("missing country"),
    club = json.getString("club") ?: default?.club ?: badRequest("missing club")
).also { player ->
    player.skip.clear()
    json.getArray("skip")?.let {
        if (it.isNotEmpty()) player.skip.addAll(it.map { id -> (id as Number).toInt() })
    }
}
