package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import kotlin.properties.Delegates

class SwissSolver(round: Int,
                  history: List<List<Game>>,
                  pairables: List<Pairable>,
                  pairingParams: PairingParams,
                  placementParams: PlacementParams):
        Solver(round, history, pairables, pairingParams, placementParams) {

    // In a Swiss tournament the main criterion is the number of wins and already computed

    override val scores by lazy {
        historyHelper.wins
    }
    //
    // get() by lazy { historyHelper.wins }

    override val mainLimits = Pair(0.0, round - 1.0)
}
