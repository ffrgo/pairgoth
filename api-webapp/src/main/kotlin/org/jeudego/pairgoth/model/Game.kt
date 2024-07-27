package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game.Result.*
import java.util.*

data class Game(
    val id: ID,
    var table: Int,
    var white: ID,
    var black: ID,
    var handicap: Int = 0,
    var result: Result = UNKNOWN,
    var drawnUpDown: Int = 0, // counted for white (black gets the opposite)
    var forcedTable: Boolean = false
) {

    companion object {}
    enum class Result(val symbol: Char) {
        UNKNOWN('?'),
        BLACK('b'),
        WHITE('w'),
        JIGO('='),
        CANCELLED('X'),
        BOTHWIN('#'),
        BOTHLOOSE('0');

        companion object {
            private val byChar = Result.values().associateBy { it.symbol }
            fun fromSymbol(c: Char) = byChar[c] ?: throw Error("unknown result symbol: $c")
        }
    }

    fun bipPlayed(): Boolean {
        return white == ByePlayer.id ||black == ByePlayer.id
    }
}

// serialization

fun Game.toJson() = Json.Object(
    "id" to id,
    "t" to table,
    "w" to white,
    "b" to black,
    "h" to handicap,
    "r" to "${result.symbol}",
    "dd" to drawnUpDown,
    "ft" to forcedTable
)

fun Game.Companion.fromJson(json: Json.Object) = Game(
    id = json.getID("id") ?: throw Error("missing game id"),
    table = json.getInt("t") ?: throw Error("missing game table"),
    white = json.getID("w") ?: throw Error("missing white player"),
    black = json.getID("b") ?: throw Error("missing black player"),
    handicap = json.getInt("h") ?: 0,
    result = json.getChar("r")?.let { Game.Result.fromSymbol(it) } ?: UNKNOWN,
    drawnUpDown = json.getInt("dd") ?: 0,
    forcedTable = json.getBoolean("ft") ?: false
)
