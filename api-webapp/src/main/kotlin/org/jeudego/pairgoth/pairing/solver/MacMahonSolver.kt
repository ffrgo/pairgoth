package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.pairing.HistoryHelper
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MacMahonSolver(round: Int,
                     totalRounds: Int,
                     history: HistoryHelper,
                     pairables: List<Pairable>,
                     allPairablesMap: Map<ID, Pairable>,
                     pairingParams: PairingParams,
                     placementParams: PlacementParams,
                     usedTables: BitSet,
                     private val mmFloor: Int, private val mmBar: Int)  :
    Solver(round, totalRounds, history, pairables, allPairablesMap, pairingParams, placementParams, usedTables) {

    override fun mainScoreMapFactory() =
        allPairablesMap.mapValues { (id, pairable) ->
            roundScore(pairable.mmBase +
                    pairable.nbW +
                    pairable.missedRounds() * pairing.main.mmsValueAbsent)
        }

    override fun scoreXMapFactory() =
        allPairablesMap.mapValues { (id, pairable) ->
            roundScore(pairable.mmBase + pairable.nbW)
        }

    override fun missedRoundSosMapFactory() =
        allPairablesMap.mapValues { (id, pairable) ->
            if (pairing.main.sosValueAbsentUseBase) {
                pairable.mmBase
            } else {
                roundScore(pairable.mmBase + round/2)
            }
        }

    override fun computeWeightForBye(p: Pairable): Double{
        return 2 * p.score
    }

    override fun SecondaryCritParams.playersMeetCriteria(p1: Pairable, p2: Pairable): Int {
        // playersMeetCriteria = 0 : No player is above thresholds -> apply the full weight
        // playersMeetCriteria = 1 : 1 player is above thresholds -> apply half the weight
        // playersMeetCriteria = 2 : Both players are above thresholds -> do not apply weight

        var playersMeetCriteria = 0

        val nbw2Threshold =
            if (nbWinsThresholdActive) totalRounds
            else 2 * totalRounds

        // Test whether each pairable meets one of the criteria
        // subtract Pairable.MIN_RANK to thresholds to convert ranks to MMS score
        if (2 * p1.nbW >= nbw2Threshold
            // check if STARTING MMS is above MM bar (OpenGotha v3.52 behavior)
            || barThresholdActive && (p1.mmBase >= mmBar - Pairable.MIN_RANK)
            || p1.mms >= rankSecThreshold - Pairable.MIN_RANK) playersMeetCriteria++

        if (2 * p2.nbW >= nbw2Threshold
            // check if STARTING MMS is above MM bar (OpenGotha v3.52 behavior)
            || barThresholdActive && (p2.mmBase >= mmBar - Pairable.MIN_RANK)
            || p2.mms >= rankSecThreshold - Pairable.MIN_RANK) playersMeetCriteria++

        return playersMeetCriteria
    }

    override fun HandicapParams.pseudoRank(pairable: Pairable): Int {
        if (useMMS) {
            return pairable.mms.roundToInt() + Pairable.MIN_RANK
        } else {
            return pairable.rank
        }
    }

    // mmBase: starting Mac-Mahon score of the pairable
    val Pairable.mmBase: Double get() = min(max(rank, mmFloor), mmBar) + mmsZero + mmsCorrection
    // mms: current Mac-Mahon score of the pairable
    val Pairable.mms: Double get() = score

    // CB TODO - configurable criteria
    val mainScoreMin = mmFloor + PLA_SMMS_SCORE_MIN - Pairable.MIN_RANK
    val mainScoreMax = mmBar + PLA_SMMS_SCORE_MAX + (round-1) - Pairable.MIN_RANK // round number starts at 1
    override val mainLimits get() = Pair(mainScoreMin.toDouble(), mainScoreMax.toDouble())
    override fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.MMS -> pairable.mms
        Criterion.SCOREX -> pairable.scoreX
        Criterion.SOSM -> pairable.sos
        Criterion.SOSOSM -> pairable.sosos
        Criterion.SOSMM1 -> pairable.sosm1
        Criterion.SOSMM2 -> pairable.sosm2
        else -> super.evalCriterion(pairable, criterion)
    }

    companion object {
        const val mmsZero = 30.0
        const val PLA_SMMS_SCORE_MAX = 2 // TODO move this into placement criteria
        const val PLA_SMMS_SCORE_MIN = -1
    }

}
