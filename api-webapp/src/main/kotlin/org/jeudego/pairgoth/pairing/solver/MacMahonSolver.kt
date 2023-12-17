package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import kotlin.math.max
import kotlin.math.min

class MacMahonSolver(round: Int,
                     history: List<List<Game>>,
                     pairables: List<Pairable>,
                     pairingParams: PairingParams,
                     placementParams: PlacementParams,
                     private val mmFloor: Int, private val mmBar: Int):
    BaseSolver(round, history, pairables, pairingParams, placementParams) {

    override val scores: Map<ID, Double> by lazy {
        historyHelper.wins.mapValues {
            pairablesMap[it.key]!!.let { pairable ->
                pairable.mmBase + pairable.nbW
            }
        }
    }
    val Pairable.mmBase: Double get() = min(max(rank, mmFloor), mmBar) + mmsZero
    val Pairable.mms: Double get() = scores[id] ?: 0.0

    // CB TODO - configurable criteria
    override val mainLimits get() = Pair(mmFloor.toDouble(), 100.0) // TODO ?
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
    }

}
