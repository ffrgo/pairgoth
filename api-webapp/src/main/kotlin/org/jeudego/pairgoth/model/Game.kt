package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game.Result.*
import java.util.*

data class Game(
    val id: ID,
    var white: ID,
    var black: ID,
    var handicap: Int = 0,
    var result: Result = UNKNOWN,
    var drawnUpDown: Int = 0 // counted for white (black gets the opposite)
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
}

// serialization

fun Game.toJson() = Json.Object(
    "id" to id,
    "w" to white,
    "b" to black,
    "h" to handicap,
    "r" to "${result.symbol}",
    "dd" to drawnUpDown
)

fun Game.Companion.fromJson(json: Json.Object) = Game(
    id = json.getID("id") ?: throw Error("missing game id"),
    white = json.getID("white") ?: throw Error("missing white player"),
    black = json.getID("black") ?: throw Error("missing black player"),
    handicap = json.getInt("handicap") ?: 0,
    result = json.getChar("result")?.let { Game.Result.fromSymbol(it) } ?: UNKNOWN,
    drawnUpDown = json.getInt("drawnUpDown") ?: 0
)
