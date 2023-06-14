package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import kotlin.math.abs

class SwissSolver(round: Int,
                  history: List<Game>,
                  pairables: List<Pairable>,
                  pairingParams: Pairing.PairingParams,
                  placementParams: PlacementParams):
        Solver(round, history, pairables, pairingParams, placementParams) {

    // In a Swiss tournament the main criterion is the number of wins and already computed
    override fun mainCriterion(p1: Pairable): Int {
        return p1.nbW.toInt() // Rounded Down TODO make it a parameter ?
    }

    override fun mainCriterionMinMax(): Pair<Int, Int> {
        return Pair(0, round-1)
    }

    override fun computeStandingScore(): Map<ID, Double> {
        return historyHelper.numberWins
    }

    override fun getSpecificCriterionValue(p: Pairable, criterion: PlacementCriterion): Double {
        // TODO solve this double/int conflict
        return when (criterion) {
            PlacementCriterion.NBW -> p.nbW
            PlacementCriterion.SOSW -> p.sos
            PlacementCriterion.SOSWM1 -> p.sosm1
            PlacementCriterion.SOSWM2 -> p.sosm2
            PlacementCriterion.SODOSW -> p.sodos
            PlacementCriterion.SOSOSW -> p.sosos
            PlacementCriterion.CUSSW -> p.cums
            else -> -1.0
        }
    }
}
