package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MacMahonSolver(round: Int,
                     history: List<List<Game>>,
                     pairables: List<Pairable>,
                     pairingParams: PairingParams,
                     placementParams: PlacementParams,
                     usedTables: BitSet,
                     private val mmFloor: Int, private val mmBar: Int):
    BaseSolver(round, history, pairables, pairingParams, placementParams, usedTables) {

    override val scores: Map<ID, Double> by lazy {
        pairablesMap.mapValues {
            it.value.let {
                pairable -> pairable.mmBase +
                    pairable.nbW + // TODO take tournament parameter into account
                    pairable.missedRounds(round) * pairingParams.main.mmsValueAbsent
            }
        }
    }

    val Pairable.mmBase: Double get() = min(max(rank, mmFloor), mmBar) + mmsZero
    val Pairable.mms: Double get() = scores[id] ?: 0.0

    // CB TODO - configurable criteria
    val mainScoreMin = mmFloor + PLA_SMMS_SCORE_MIN - Pairable.MIN_RANK
    val mainScoreMax = mmBar + PLA_SMMS_SCORE_MAX + (round-1) - Pairable.MIN_RANK // round number starts at 1
    override val mainLimits get() = Pair(mainScoreMin.toDouble(), mainScoreMax.toDouble())
    override fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.MMS -> pairable.mms
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
