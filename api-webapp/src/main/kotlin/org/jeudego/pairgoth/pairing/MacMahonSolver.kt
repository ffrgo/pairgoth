package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

class MacMahonSolver(round: Int,
                     history: List<List<Game>>,
                     pairables: List<Pairable>,
                     pairingParams: PairingParams,
                     placementParams: PlacementParams):
    Solver(round, history, pairables, pairingParams, placementParams) {

    val Pairable.mmBase: Double get() = rank + 30.0 // TODO use params
    val Pairable.mms: Double get() = mmBase + nbW // TODO real calculation

    // CB TODO - configurable criteria
    override val Pairable.main get() = mms
    override val mainLimits get() = TODO()
    override fun computeStandingScore(): Map<ID, Double> {
        TODO("Not yet implemented")
    }

    override fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.MMS -> pairable.mms
        else -> super.evalCriterion(pairable, criterion)
    }

}
