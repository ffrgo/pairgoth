package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Swiss
import kotlin.math.abs

class SwissSolver(history: List<Game>, method: Swiss.Method): Solver(history) {

    override fun sort(p: Pairable, q: Pairable): Int =
        when (p.score) {
            q.score -> p.rating - q.rating
            else -> p.score - q.score
        }

    override fun weight(p: Pairable, q: Pairable) = when {
        p.played(q) -> 100_000.0
        p.score != q.score -> abs(p.score - q.score) * 10_000.0
        else -> abs(p.rating - q.rating) * 10.0
    }
}
