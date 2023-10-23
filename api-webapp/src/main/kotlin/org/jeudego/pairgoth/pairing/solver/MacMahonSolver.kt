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
        else -> super.evalCriterion(pairable, criterion)
    }

}
