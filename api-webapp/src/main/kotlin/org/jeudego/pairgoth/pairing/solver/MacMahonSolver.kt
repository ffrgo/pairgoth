package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*

class MacMahonSolver(round: Int,
                     history: List<List<Game>>,
                     pairables: List<Pairable>,
                     pairingParams: PairingParams,
                     placementParams: PlacementParams):
    BaseSolver(round, history, pairables, pairingParams, placementParams) {

    override val scores: Map<ID, Double> by lazy {
        historyHelper.wins.mapValues {
            pairablesMap[it.key]!!.let { pairable ->
                pairable.mmBase + pairable.nbW
            }
        }
    }
    val Pairable.mmBase: Double get() = rank + 30.0    // TODO use params
    val Pairable.mms: Double get() = scores[id] ?: 0.0

    // CB TODO - configurable criteria
    override val mainLimits get() = Pair(0.0, 100.0) // TODO
    override fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.MMS -> pairable.mms
        Criterion.SOSM -> pairable.sos
        Criterion.SOSOSM -> pairable.sosos
        Criterion.SOSMM1 -> pairable.sosm1
        Criterion.SOSMM2 -> pairable.sosm2
        else -> super.evalCriterion(pairable, criterion)
    }

}
