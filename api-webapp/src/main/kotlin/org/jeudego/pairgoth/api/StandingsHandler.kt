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
import org.jeudego.pairgoth.model.Game.Result.*
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

        //  CB avoid code redundancy with solvers
        val historyHelper = HistoryHelper(tournament.historyBefore(round + 1)) {
            if (tournament.pairing.type == PairingType.SWISS) wins
            else tournament.pairables.mapValues {
                it.value.let {
                    pairable ->
                        mmBase(pairable) +
                        (nbW(pairable) ?: 0.0) + // TODO take tournament parameter into account
                        (1..round).map { round ->
                            if (playersPerRound.getOrNull(round - 1)?.contains(pairable.id) == true) 0 else 1
                        }.sum() * tournament.pairing.pairingParams.main.mmsValueAbsent
                }
            }
        }
        val neededCriteria = ArrayList(tournament.pairing.placementParams.criteria)
        if (!neededCriteria.contains(NBW)) neededCriteria.add(NBW)
        val criteria = neededCriteria.map { crit ->
            crit.name to when (crit) {
                NONE -> nullMap
                CATEGORY -> nullMap
                RANK -> tournament.pairables.mapValues { it.value.rank }
                RATING -> tournament.pairables.mapValues { it.value.rating }
                NBW -> historyHelper.wins
                MMS -> historyHelper.mms
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
        pairables.forEach { player ->
            for (crit in criteria) {
                player[crit.first] = crit.second[player.getID()] ?: 0.0
            }
            player["results"] = Json.MutableArray(List(round) { "=0" })
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
        val sortedMap = sortedPairables.associateBy {
            it.getID()!!
        }
        var place = 1
        sortedPairables.groupBy { p ->
            Triple(p.getDouble(criteria[0].first) ?: 0.0, p.getDouble(criteria[1].first)  ?: 0.0, p.getDouble(criteria[2].first)  ?: 0.0)
        }.forEach {
            it.value.forEach { p -> p["place"] = place }
            place += it.value.size
        }
        for (r in 1..round) {
            tournament.games(r).values.forEach { game ->
                val white = if (game.white != 0) sortedMap[game.white] else null
                val black = if (game.black != 0) sortedMap[game.black] else null
                val whiteNum = white?.getInt("num") ?: 0
                val blackNum = black?.getInt("num") ?: 0
                val whiteColor = if (black == null) "" else "w"
                val blackColor = if (white == null) "" else "b"
                val handicap = if (game.handicap == 0) "" else "/h${game.handicap}"
                assert(white != null || black != null)
                if (white != null) {
                    val mark =  when (game.result) {
                        UNKNOWN -> "?"
                        BLACK -> "-"
                        WHITE -> "+"
                        JIGO -> "="
                        CANCELLED -> "X"
                        BOTHWIN -> "++"
                        BOTHLOOSE -> "--"
                    }
                    val results = white.getArray("results") as Json.MutableArray
                    results[r - 1] = "$whiteColor$mark$blackNum$handicap"
                }
                if (black != null) {
                    val mark =  when (game.result) {
                        UNKNOWN -> "?"
                        BLACK -> "+"
                        WHITE -> "-"
                        JIGO -> "="
                        CANCELLED -> "X"
                        BOTHWIN -> "++"
                        BOTHLOOSE -> "--"
                    }
                    val results = black.getArray("results") as Json.MutableArray
                    results[r - 1] = "$blackColor$mark$whiteNum$handicap"
                }
            }
        }
        return sortedPairables.toJsonArray()
    }

    val nullMap = mapOf<ID, Double>()
}
