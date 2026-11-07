package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.pairing.HistoryHelper
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * The "French system": a Swiss whose main score carries a starting-level bonus that fades round
 * after round. At round k of r, the round's participants are split by starting level into r-k
 * groups of equal size (remainder in the bottom group); the top group gets r-k bonus points, the
 * next r-k-1, ..., the bottom one 1. The last two rounds are a plain Swiss (one group, then none).
 */
class FrenchSolver(round: Int,
                   totalRounds: Int,
                   history: HistoryHelper,
                   pairables: List<Pairable>,
                   allPairablesMap: Map<ID, Pairable>,
                   pairingParams: PairingParams,
                   placementParams: PlacementParams,
                   usedTables: BitSet
):
    Solver(round, totalRounds, history, pairables, allPairablesMap, pairingParams, placementParams, usedTables) {

    // The field is the round's participants, read from the whole map: the solver may be given a
    // subset (partial pairing) or nothing at all (standings), and the bonus must not depend on that.
    private val bonus: Map<ID, Int> by lazy {
        val groups = totalRounds - round
        val field = allPairablesMap.values
            .filter { it.id != ByePlayer.id && it.final && it.canPlay(round) }
            .sortedWith(compareByDescending<Pairable> { it.rating }.thenByDescending { it.rank }.thenBy { it.fullName() })
        if (groups <= 0) emptyMap()
        else {
            val size = max(1, field.size / groups)
            field.mapIndexed { index, pairable ->
                pairable.id to groups - min(index / size, groups - 1)
            }.toMap()
        }
    }

    override fun mainScoreMapFactory() =
        allPairablesMap.mapValues { (id, _) ->
            (history.nbwScores[id] ?: 0.0) + (bonus[id] ?: 0)
        }

    override fun scoreXMapFactory() = mainScoreMapFactory()

    override fun missedRoundSosMapFactory() =
        allPairablesMap.mapValues { 0.0 }

    // the top score is the same at every round: r-1 bonus points at round 1, r-1 wins at round r
    override val mainLimits = Pair(0.0, max(0, totalRounds - 1).toDouble())

    override fun computeWeightForBye(p: Pairable): Double {
        return p.computedRank + 40 * p.main
    }

    override fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.MMS -> pairable.score
        Criterion.SCOREX -> pairable.scoreX
        Criterion.SOSM -> pairable.sos
        Criterion.SOSOSM -> pairable.sosos
        Criterion.SOSMM1 -> pairable.sosm1
        Criterion.SOSMM2 -> pairable.sosm2
        else -> super.evalCriterion(pairable, criterion)
    }
}
