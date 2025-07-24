package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.pairing.HistoryHelper
import java.util.*

class SwissSolver(round: Int,
                  totalRounds: Int,
                  history: HistoryHelper,
                  pairables: List<Pairable>,
                  allPairablesMap: Map<ID, Pairable>,
                  pairingParams: PairingParams,
                  placementParams: PlacementParams,
                  usedTables: BitSet
):
    Solver(round, totalRounds, history, pairables, allPairablesMap, pairingParams, placementParams, usedTables) {

    override fun mainScoreMapFactory() =
        allPairablesMap.mapValues { (id, pairable) ->
            history.wins[id] ?: 0.0
        }

    override fun scoreXMapFactory() = mainScoreMapFactory()

    override fun missedRoundSosMapFactory() =
        allPairablesMap.mapValues { (id, pairable) ->
            0.0
        }

    override val mainLimits = Pair(0.0, round - 1.0)

    override fun computeWeightForBye(p: Pairable): Double{
        return p.rank + 40 * p.main
    }
}
