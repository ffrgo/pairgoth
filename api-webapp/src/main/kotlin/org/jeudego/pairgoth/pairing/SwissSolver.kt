package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

class SwissSolver(round: Int,
                  history: List<List<Game>>,
                  pairables: List<Pairable>,
                  pairingParams: PairingParams,
                  placementParams: PlacementParams):
        Solver(round, history, pairables, pairingParams, placementParams) {

    // In a Swiss tournament the main criterion is the number of wins and already computed
    override val Pairable.main: Double get() = nbW // Rounded Down TODO make it a parameter ?
    override val mainLimits = Pair(0.0, round - 1.0)

    override fun computeStandingScore(): Map<ID, Double> {
        return historyHelper.wins
    }
}
