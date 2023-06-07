package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

class MacMahonSolver(history: List<Game>, pairables: List<Pairable>, pairingParams: Pairing.PairingParams, val mmBase: Int, val mmBar: Int, val reducer: Int): Solver(history, pairables, pairingParams) {

    val Pairable.mms get() = mmBase + score

    // CB TODO - configurable criteria
    override fun mainCriterion(p1: Pairable): Int {
        TODO("Not yet implemented")
    }

    override fun mainCriterionMinMax(): Pair<Int, Int> {
        TODO("Not yet implemented")
    }
    override fun sort(p: Pairable, q: Pairable): Int =
        if (p.mms != q.mms) ((q.mms - p.mms) * 1000).toInt()
        else if (p.sos != q.sos) ((q.sos - p.sos) * 1000).toInt()
        else if (p.sosos != q.sosos) ((q.sosos - p.sosos) * 1000).toInt()
        else 0

}
