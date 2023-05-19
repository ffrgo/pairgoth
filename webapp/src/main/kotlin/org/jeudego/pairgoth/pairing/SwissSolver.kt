package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.Swiss
import org.jeudego.pairgoth.model.Swiss.Method.*
import kotlin.math.abs

class SwissSolver(history: List<Game>, pairables: List<Pairable>, weights: Pairing.Weights, val method: Swiss.Method): Solver(history, pairables, weights) {

    override fun sort(p: Pairable, q: Pairable): Int =
        when (p.score) {
            q.score -> p.rating - q.rating
            else -> p.score - q.score
        }

    override fun weight(p: Pairable, q: Pairable) = when {
        p.played(q) -> weights.played
        p.score != q.score -> {
            val placeWeight =
                if (p.score > q.score) (p.placeInGroup.second + q.placeInGroup.first) * weights.place
                else (q.placeInGroup.second + p.placeInGroup.first) * weights.place
            abs(p.score - q.score) * weights.score + placeWeight
        }
        else -> when (method) {
            SPLIT_AND_FOLD ->
                if (p.placeInGroup.first > q.placeInGroup.first) abs(p.placeInGroup.first - (q.placeInGroup.second - q.placeInGroup.first)) * weights.place
                else abs(q.placeInGroup.first - (p.placeInGroup.second - p.placeInGroup.first)) * weights.place
            SPLIT_AND_RANDOM -> rand.nextDouble() * p.placeInGroup.second * weights.place
            SPLIT_AND_SLIP -> abs(abs(p.placeInGroup.first - q.placeInGroup.first) - p.placeInGroup.second) * weights.place
            else -> throw Error("unhandled case")
        }
    } + (abs(p.colorBalance + 1) + abs(q.colorBalance - 1)) * weights.color
}
