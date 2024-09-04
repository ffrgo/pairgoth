package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import java.util.*

class SwissSolver(round: Int,
                  totalRounds: Int,
                  history: List<List<Game>>,
                  pairables: List<Pairable>,
                  pairablesMap: Map<ID, Pairable>,
                  pairingParams: PairingParams,
                  placementParams: PlacementParams,
                  usedTables: BitSet
):
    BaseSolver(round, totalRounds, history, pairables, pairablesMap, pairingParams, placementParams, usedTables) {

    // In a Swiss tournament the main criterion is the number of wins and already computed

    override val scores by lazy {
        pairablesMap.mapValues {
            Pair(0.0, historyHelper.wins[it.value.id] ?: 0.0)
        }
    }
    override val scoresX: Map<ID, Double> get() = scores.mapValues { it.value.second }

    override val mainLimits = Pair(0.0, round - 1.0)
}
