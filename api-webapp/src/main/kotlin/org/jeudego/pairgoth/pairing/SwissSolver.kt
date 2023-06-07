package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import kotlin.math.abs

class SwissSolver(history: List<Game>, pairables: List<Pairable>, pairingParams: Pairing.PairingParams): Solver(history, pairables, pairingParams) {

    override fun sort(p: Pairable, q: Pairable): Int =
        when (p.score) {
            q.score -> q.rating - p.rating
            else -> ((q.score - p.score) * 1000).toInt()
        }

    override fun mainCriterion(p1: Pairable): Int {
        TODO("Not yet implemented")
    }

    override fun mainCriterionMinMax(): Pair<Int, Int> {
        TODO("Not yet implemented")
    }
}
