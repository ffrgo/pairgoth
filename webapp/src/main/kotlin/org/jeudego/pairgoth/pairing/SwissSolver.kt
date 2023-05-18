package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Swiss
import org.jeudego.pairgoth.model.Swiss.Method.*
import kotlin.math.abs

class SwissSolver(history: List<Game>, pairables: List<Pairable>, val method: Swiss.Method): Solver(history, pairables) {

    val PLAYED_WEIGHT = 1_000_000.0 // weight if players already met
    val SCORE_WEIGHT = 10_000.0     // weight per difference of score
    val PLACE_WEIGHT = 1_000.0      // weight per difference of place

    override fun sort(p: Pairable, q: Pairable): Int =
        when (p.score) {
            q.score -> p.rating - q.rating
            else -> p.score - q.score
        }

    override fun weight(p: Pairable, q: Pairable) = when {
        p.played(q) -> PLAYED_WEIGHT
        p.score != q.score -> {
            val placeWeight =
                if (p.score > q.score) (p.placeInGroup.second + q.placeInGroup.first) * PLACE_WEIGHT
                else (q.placeInGroup.second + p.placeInGroup.first) * PLACE_WEIGHT
            abs(p.score - q.score) * SCORE_WEIGHT + placeWeight
        }
        else -> when (method) {
            SPLIT_AND_FOLD ->
                if (p.placeInGroup.first > q.placeInGroup.first) abs(p.placeInGroup.first - (q.placeInGroup.second - q.placeInGroup.first)) * PLACE_WEIGHT
                else abs(q.placeInGroup.first - (p.placeInGroup.second - p.placeInGroup.first)) * PLACE_WEIGHT
            SPLIT_AND_RANDOM -> rand.nextDouble(p.placeInGroup.second.toDouble()) * PLACE_WEIGHT
            SPLIT_AND_SLIP -> abs(abs(p.placeInGroup.first - q.placeInGroup.first) - p.placeInGroup.second) * PLACE_WEIGHT
            else -> throw Error("unhandled case")
        }
    }
}
