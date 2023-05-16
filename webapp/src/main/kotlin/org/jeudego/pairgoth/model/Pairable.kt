package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import kotlin.math.roundToInt

// Pairable

sealed class Pairable(val id: Int, val name: String, open val rating: Int, open val rank: Int) {
    abstract fun toJson(): Json.Object
    val skip = mutableSetOf<Int>() // skipped rounds
}

fun Pairable.displayRank(): String = when {
    rank < 0 -> "${-rank}k"
    rank >= 0 && rank < 10 -> "${rank + 1}d"
    rank >= 10 -> "${rank - 9}p"
    else -> throw Error("impossible")
}

private val rankRegex = Regex("(\\d+)([kdp])", RegexOption.IGNORE_CASE)

fun Pairable.setRank(rankStr: String): Int {
    val (level, letter) = rankRegex.matchEntire(rankStr)?.destructured ?: throw Error("invalid rank: $rankStr")
    val num = level.toInt()
    if (num < 0 || num > 9) throw Error("invalid rank: $rankStr")
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
    var country: String,
    var club: String
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

// Team

class Team(id: Int, name: String): Pairable(id, name, 0, 0) {
    companion object {}
    val players = mutableSetOf<Player>()
    override val rating: Int get() = if (players.isEmpty()) super.rating else (players.sumOf { player -> player.rating.toDouble() } / players.size).roundToInt()
    override val rank: Int get() = if (players.isEmpty()) super.rank else (players.sumOf { player -> player.rank.toDouble() } / players.size).roundToInt()
    override fun toJson() = Json.Object(
        "id" to id,
        "name" to name,
        "players" to Json.Array(players.map { it.toJson() })
    )
}

fun Team.Companion.fromJson(json: Json.Object) = Team(
    id = json.getInt("id") ?: Store.nextPlayerId,
    name = json.getString("name") ?: badRequest("missing name")
).apply {
    json.getArray("players")?.let { arr ->
        arr.map {
            if (it != null && it is Json.Object) Player.fromJson(it)
            else badRequest("invalid players array")
        }
    } ?: badRequest("missing players")
}
