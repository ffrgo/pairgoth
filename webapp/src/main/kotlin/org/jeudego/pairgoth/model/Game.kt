package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game.Result.*
import java.util.*

data class Game(
    val id: ID,
    var white: ID,
    var black: ID,
    var handicap: Int = 0,
    var result: Result = UNKNOWN
) {
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
    "r" to "${result.symbol}"
)
