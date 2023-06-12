package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

class MacMahonSolver(round: Int, history: List<Game>, pairables: List<Pairable>, pairingParams: Pairing.PairingParams, placementParams: PlacementParams, val mmBase: Int, val mmBar: Int, val reducer: Int): Solver(round, history, pairables, pairingParams, placementParams) {

    val Pairable.mms get() = mmBase + nbW // TODO real calculation

    // CB TODO - configurable criteria
    override fun mainCriterion(p1: Pairable): Int {
        TODO("Not yet implemented")
    }

    override fun mainCriterionMinMax(): Pair<Int, Int> {
        TODO("Not yet implemented")
    }

    override fun computeStandingScore(): Map<ID, Double> {
        TODO("Not yet implemented")
    }

    override fun getSpecificCriterionValue(p: Pairable, criterion: PlacementCriterion): Int {
        // TODO solve this double/int conflict
        return when (criterion) {
            PlacementCriterion.MMS -> TODO()
            PlacementCriterion.SOSM -> p.sos.toInt()
            PlacementCriterion.SOSMM1 -> p.sosm1.toInt()
            PlacementCriterion.SOSMM2 -> p.sosm2.toInt()
            PlacementCriterion.SODOSM -> p.sodos.toInt()
            PlacementCriterion.SOSOSM -> p.sosos.toInt()
            PlacementCriterion.CUSSM -> p.cums.toInt()
            else -> -1
        }
    }

}
