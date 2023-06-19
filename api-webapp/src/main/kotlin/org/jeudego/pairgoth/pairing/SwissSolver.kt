package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

class SwissSolver(round: Int,
                  history: List<Game>,
                  pairables: List<Pairable>,
                  pairingParams: PairingParams,
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

    override fun getSpecificCriterionValue(p: Pairable, criterion: Criterion): Double {
        // TODO solve this double/int conflict
        return when (criterion) {
            Criterion.NBW -> p.nbW
            Criterion.SOSW -> p.sos
            Criterion.SOSWM1 -> p.sosm1
            Criterion.SOSWM2 -> p.sosm2
            Criterion.SODOSW -> p.sodos
            Criterion.SOSOSW -> p.sosos
            Criterion.CUSSW -> p.cums
            else -> -1.0
        }
    }
}
