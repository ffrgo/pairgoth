package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.store.nextPlayerId
import java.util.*

// Pairable

sealed class Pairable(val id: ID, val name: String, open val rating: Int, open val rank: Int, val final: Boolean, val mmsCorrection: Int = 0) {
    companion object {
        const val MIN_RANK: Int = -30 // 30k
        const val MAX_RANK: Int = 8 // 9D
    }
    abstract fun toJson(): Json.Object
    abstract fun toMutableJson(): Json.MutableObject
    abstract val club: String?
    abstract val country: String?
    open fun fullName(separator: String = " "): String {
        return name
    }
    val skip = mutableSetOf<Int>() // skipped rounds

    fun equals(other: Pairable): Boolean {
        return id == other.id
    }
}

object ByePlayer: Pairable(0, "bye", 0, Int.MIN_VALUE, true) {
    override fun toJson(): Json.Object {
        throw Error("bye player should never be serialized")
    }
    override fun toMutableJson(): Json.MutableObject {
        throw Error("bye player should never be serialized")
    }

    override val club = "none"
    override val country = "none"
}

fun displayRank(rank: Int) = if (rank < 0) "${-rank}k" else "${rank + 1}d"
fun Pairable.displayRank() = displayRank(rank)

private val rankRegex = Regex("(\\d+)([kd])", RegexOption.IGNORE_CASE)

fun Pairable.Companion.parseRank(rankStr: String): Int {
    val (level, letter) = rankRegex.matchEntire(rankStr)?.destructured ?: throw Error("invalid rank: $rankStr")
    val num = level.toInt()
    if (num < 0 || letter != "k" && letter != "K" && num > 9) throw Error("invalid rank: $rankStr")
    return when (letter.lowercase()) {
        "k" -> -num
        "d" -> num - 1
        else -> throw Error("impossible")
    }
}

// Player

enum class DatabaseId {
    AGA,
    EGF,
    FFG;
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

    override fun toJson(): Json.Object = toMutableJson()
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
        // EGC uses UK, while FFG and browser language use GB
        val up = it.uppercase(Locale.ROOT)
        if (up == "UK") "GB" else up
    },
    club = json.getString("club") ?: default?.club ?: badRequest("missing club"),
    final = json.getBoolean("final") ?: default?.final ?: true,
    mmsCorrection = json.getInt("mmsCorrection") ?: default?.mmsCorrection ?: 0
).also { player ->
    player.skip.clear()
    json.getArray("skip")?.let {
        if (it.isNotEmpty()) player.skip.addAll(it.map { id -> (id as Number).toInt() })
    }
    DatabaseId.values().forEach { dbid ->
        json.getString(dbid.key)?.let { id ->
            player.externalIds[dbid] = id
        }
    }
}
