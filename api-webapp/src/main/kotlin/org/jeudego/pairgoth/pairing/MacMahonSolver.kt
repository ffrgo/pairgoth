package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

class MacMahonSolver(round: Int, history: List<Game>, pairables: List<Pairable>, pairingParams: PairingParams, placementParams: PlacementParams): Solver(round, history, pairables, pairingParams, placementParams) {

//    val Pairable.mms get() = mmBase + nbW // TODO real calculation

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

    override fun getSpecificCriterionValue(p: Pairable, criterion: Criterion): Double {
        // TODO solve this double/int conflict
        return when (criterion) {
            Criterion.MMS -> TODO()
            Criterion.SOSM -> p.sos
            Criterion.SOSMM1 -> p.sosm1
            Criterion.SOSMM2 -> p.sosm2
            Criterion.SODOSM -> p.sodos
            Criterion.SOSOSM -> p.sosos
            Criterion.CUSSM -> p.cums
            else -> -1.0
        }
    }

}
