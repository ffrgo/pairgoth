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
            q.score -> q.rating - p.rating
            else -> ((q.score - p.score) * 1000).toInt()
        }

    override fun weight(black: Pairable, white: Pairable): Double {
        var weight = 0.0
        if (black.played(white)) weight += weights.played
        if (black.score != white.score) {
            val placeWeight =
                if (black.score > white.score) (black.placeInGroup.second + white.placeInGroup.first) * weights.place
                else (white.placeInGroup.second + black.placeInGroup.first) * weights.place
            weight += abs(black.score - white.score) * weights.score + placeWeight
        } else {
            weight += when (method) {
                SPLIT_AND_FOLD ->
                    if (black.placeInGroup.first > white.placeInGroup.first) abs(black.placeInGroup.first - (white.placeInGroup.second - white.placeInGroup.first)) * weights.place
                    else abs(white.placeInGroup.first - (black.placeInGroup.second - black.placeInGroup.first)) * weights.place

                SPLIT_AND_RANDOM -> rand.nextDouble() * black.placeInGroup.second * weights.place
                SPLIT_AND_SLIP -> abs(abs(black.placeInGroup.first - white.placeInGroup.first) - black.placeInGroup.second) * weights.place
            }
        }
        weight += (abs(black.colorBalance + 1) + abs(white.colorBalance - 1)) * weights.color
        return weight
    }
}
