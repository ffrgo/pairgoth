package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairable.Companion.MIN_RANK
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.historyBefore
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

//  TODO CB avoid code redundancy with solvers

fun Tournament<*>.getSortedPairables(round: Int): List<Json.Object> {

    fun Pairable.mmBase(): Double {
        if (pairing !is MacMahon) throw Error("invalid call: tournament is not Mac Mahon")
        return min(max(rank, pairing.mmFloor), pairing.mmBar) + MacMahonSolver.mmsZero + mmsCorrection
    }

    fun roundScore(score: Double): Double {
        val epsilon = 0.00001
        // Note: this works for now because we only have .0 and .5 fractional parts
        return if (pairing.pairingParams.main.roundDownScore) floor(score + epsilon)
        else ceil(score - epsilon)
    }

    val historyHelper = HistoryHelper(historyBefore(round + 1)) {
        if (pairing.type == PairingType.SWISS) wins.mapValues { Pair(0.0, it.value) }
        else pairables.mapValues {
            it.value.let { pairable ->
                val mmBase = pairable.mmBase()
                Pair(
                    mmBase,
                    roundScore(mmBase +
                            (nbW(pairable) ?: 0.0) + // TODO take tournament parameter into account
                            (1..round).map { round ->
                                if (playersPerRound.getOrNull(round - 1)?.contains(pairable.id) == true) 0 else 1
                            }.sum() * pairing.pairingParams.main.mmsValueAbsent)
                )
            }
        }
    }
    val neededCriteria = ArrayList(pairing.placementParams.criteria)
    if (!neededCriteria.contains(Criterion.NBW)) neededCriteria.add(Criterion.NBW)
    if (!neededCriteria.contains(Criterion.RATING)) neededCriteria.add(Criterion.RATING)
    val criteria = neededCriteria.map { crit ->
        crit.name to when (crit) {
            Criterion.NONE -> StandingsHandler.nullMap
            Criterion.CATEGORY -> StandingsHandler.nullMap
            Criterion.RANK -> pairables.mapValues { it.value.rank }
            Criterion.RATING -> pairables.mapValues { it.value.rating }
            Criterion.NBW -> historyHelper.wins
            Criterion.MMS -> historyHelper.mms
            Criterion.STS -> StandingsHandler.nullMap
            Criterion.CPS -> StandingsHandler.nullMap

            Criterion.SOSW -> historyHelper.sos
            Criterion.SOSWM1 -> historyHelper.sosm1
            Criterion.SOSWM2 -> historyHelper.sosm2
            Criterion.SODOSW -> historyHelper.sodos
            Criterion.SOSOSW -> historyHelper.sosos
            Criterion.CUSSW -> historyHelper.cumScore
            Criterion.SOSM -> historyHelper.sos
            Criterion.SOSMM1 -> historyHelper.sosm1
            Criterion.SOSMM2 -> historyHelper.sosm2
            Criterion.SODOSM -> historyHelper.sodos
            Criterion.SOSOSM -> historyHelper.sosos
            Criterion.CUSSM -> historyHelper.cumScore

            Criterion.SOSTS -> StandingsHandler.nullMap

            Criterion.EXT -> StandingsHandler.nullMap
            Criterion.EXR -> StandingsHandler.nullMap

            Criterion.SDC -> StandingsHandler.nullMap
            Criterion.DC -> StandingsHandler.nullMap
        }
    }
    val pairables = pairables.values.filter { it.final }.map { it.toMutableJson() }
    pairables.forEach { player ->
        for (crit in criteria) {
            player[crit.first] = crit.second[player.getID()] ?: 0.0
        }
        player["results"] = Json.MutableArray(List(round) { "0=" })
    }
    val sortedPairables = pairables.sortedWith { left, right ->
        for (crit in criteria) {
            val lval = left.getDouble(crit.first) ?: 0.0
            val rval = right.getDouble(crit.first) ?: 0.0
            val cmp = lval.compareTo(rval)
            if (cmp != 0) return@sortedWith -cmp
        }
        return@sortedWith 0
    }.mapIndexed() { i, obj ->
        obj.set("num", i+1)
    }
    var place = 1
    sortedPairables.groupBy { p ->
        Triple(p.getDouble(criteria[0].first) ?: 0.0, p.getDouble(criteria[1].first)  ?: 0.0, criteria.getOrNull(2)?.let { p.getDouble(it.first)  ?: 0.0 })
    }.forEach {
        it.value.forEach { p -> p["place"] = place }
        place += it.value.size
    }
    return sortedPairables
}
