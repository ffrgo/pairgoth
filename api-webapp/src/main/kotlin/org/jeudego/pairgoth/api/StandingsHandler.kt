package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.historyBefore
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.math.max
import kotlin.math.min

import org.jeudego.pairgoth.model.Criterion.*
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.getID

object StandingsHandler: PairgothApiHandler {
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid round number")

        fun mmBase(pairable: Pairable): Double {
            if (tournament.pairing !is MacMahon) throw Error("invalid call: tournament is not Mac Mahon")
            return min(max(pairable.rank, tournament.pairing.mmFloor), tournament.pairing.mmBar) + MacMahonSolver.mmsZero
        }

        val historyHelper = HistoryHelper(tournament.historyBefore(round)) {
            if (tournament.pairing.type == PairingType.SWISS) wins
            else tournament.pairables.mapValues {
                it.value.let {
                    pairable -> mmBase(pairable) + ( nbW(pairable) ?: 0.0) // TODO take tournament parameter into account
                }
            }
        }
        val criteria = tournament.pairing.placementParams.criteria.map { crit ->
            crit.name to when (crit) {
                NONE -> nullMap
                CATEGORY -> nullMap
                RANK -> tournament.pairables.mapValues { it.value.rank }
                RATING -> tournament.pairables.mapValues { it.value.rating }
                NBW -> historyHelper.wins
                MMS -> historyHelper.scores
                STS -> nullMap
                CPS -> nullMap

                SOSW -> historyHelper.sos
                SOSWM1 -> historyHelper.sosm1
                SOSWM2 -> historyHelper.sosm2
                SODOSW -> historyHelper.sodos
                SOSOSW -> historyHelper.sosos
                CUSSW -> historyHelper.cumScore
                SOSM -> historyHelper.sos
                SOSMM1 -> historyHelper.sosm1
                SOSMM2 -> historyHelper.sosm2
                SODOSM -> historyHelper.sodos
                SOSOSM -> historyHelper.sosos
                CUSSM -> historyHelper.cumScore

                SOSTS -> nullMap

                EXT -> nullMap
                EXR -> nullMap

                SDC -> nullMap
                DC -> nullMap
            }
        }
        val pairables = tournament.pairables.values.map { it.toMutableJson() }
        pairables.forEach { it ->
            val player = it as Json.MutableObject
            for (crit in criteria) {
                player[crit.first] = crit.second[player.getID()]
            }
        }
        return pairables.sortedWith { left, right ->
            for (crit in criteria) {
                val lval = left.getDouble(crit.first) ?: 0.0
                val rval = right.getDouble(crit.first) ?: 0.0
                val cmp = lval.compareTo(rval)
                if (cmp != 0) return@sortedWith -cmp
            }
            return@sortedWith 0

        }.toJsonArray()
    }

    val nullMap = mapOf<ID, Double>()
}
