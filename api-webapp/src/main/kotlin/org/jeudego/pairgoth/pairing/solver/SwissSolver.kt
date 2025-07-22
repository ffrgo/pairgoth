package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.pairing.HistoryHelper
import java.util.*

class SwissSolver(round: Int,
                  totalRounds: Int,
                  history: HistoryHelper,
                  pairables: List<Pairable>,
                  pairablesMap: Map<ID, Pairable>,
                  pairingParams: PairingParams,
                  placementParams: PlacementParams,
                  usedTables: BitSet
):
    BaseSolver(round, totalRounds, history, pairables, pairingParams, placementParams, usedTables) {

    override val scoresX: Map<ID, Double> get() = scores.mapValues { it.value.second }

    override val mainLimits = Pair(0.0, round - 1.0)

    override fun computeWeightForBye(p: Pairable): Double{
        return p.rank + 40*p.main
    }
}
