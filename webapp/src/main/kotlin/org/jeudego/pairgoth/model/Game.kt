package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game.Result.*

data class Game(
    val id: Int,
    val white: Int,
    val black: Int,
    val handicap: Int = 0,
    var result: Result = UNKNOWN
) {
    enum class Result(val symbol: Char) { UNKNOWN('?'), BLACK('b'), WHITE('w'), JIGO('='), CANCELLED('x') }
}

// serialization

fun Game.toJson() = Json.Object(
    "id" to id,
    "w" to white,
    "b" to black,
    "r" to "${result.symbol}"
)
