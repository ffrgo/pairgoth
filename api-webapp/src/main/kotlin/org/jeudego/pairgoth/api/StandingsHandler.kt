package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.Criterion.*
import org.jeudego.pairgoth.model.Game.Result.*
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.adjustedTime
import org.jeudego.pairgoth.model.displayRank
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.historyBefore
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import java.io.PrintWriter
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.math.max
import kotlin.math.min
import org.jeudego.pairgoth.model.TimeSystem.TimeSystemType.*
import java.text.DecimalFormat

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
        val pairables = tournament.pairables.values.filter { it.final }.map { it.toMutableJson() }
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
                val handicap = if (game.handicap == 0) "" else "${game.handicap}"
                assert(white != null || black != null)
                if (white != null) {
                    val mark =  when (game.result) {
                        UNKNOWN -> "?"
                        BLACK, BOTHLOOSE -> "-"
                        WHITE, BOTHWIN -> "+"
                        JIGO, CANCELLED -> "="
                    }
                    val results = white.getArray("results") as Json.MutableArray
                    results[r - 1] =
                        if (blackNum == 0) "0$mark"
                        else "$blackNum$mark/$whiteColor$handicap"
                }
                if (black != null) {
                    val mark =  when (game.result) {
                        UNKNOWN -> "?"
                        BLACK, BOTHWIN -> "+"
                        WHITE, BOTHLOOSE -> "-"
                        JIGO, CANCELLED -> "="
                    }
                    val results = black.getArray("results") as Json.MutableArray
                    results[r - 1] =
                        if (whiteNum == 0) "0$mark"
                        else "$whiteNum$mark/$blackColor$handicap"
                }
            }
        }
        val accept = request.getHeader("Accept")?.substringBefore(";")
        return when(accept) {
            "application/json" -> sortedPairables.toJsonArray()
            "application/egf" -> {
                exportToEGFFormat(tournament, sortedPairables, neededCriteria, response.writer)
                return null
            }
            "application/ffg" -> {
                exportToFFGFormat(tournament, sortedPairables, response.writer)
                return null
            }
            else -> ApiHandler.badRequest("invalid Accept header: $accept")
        }
    }

    val nullMap = mapOf<ID, Double>()

    private fun exportToEGFFormat(tournament: Tournament<*>, lines: List<Json.Object>, criteria: List<Criterion>, writer: PrintWriter) {
        val mainTime = tournament.timeSystem.mainTime
        val adjustedTime = tournament.timeSystem.adjustedTime()
        val egfClass =
            if (tournament.online) {
                when (tournament.timeSystem.type) {
                    FISCHER ->
                        if (mainTime >= 1800 && adjustedTime >= 3000) "D"
                        else "X"
                    else ->
                        if (mainTime >= 2400 && adjustedTime >= 3000) "D"
                        else "X"
                }
            } else {
                when (tournament.timeSystem.type) {
                    FISCHER ->
                        if (mainTime >= 2700 && adjustedTime >= 4500) "A"
                        else if (mainTime >= 1800 && adjustedTime >= 3000) "B"
                        else if (mainTime >= 1200 && adjustedTime >= 1800) "C"
                        else "X"
                    else ->
                        if (mainTime >= 3600 && adjustedTime >= 4500) "A"
                        else if (mainTime >= 2400 && adjustedTime >= 3000) "B"
                        else if (mainTime >= 1500 && adjustedTime >= 1800) "C"
                        else "X"
                }
            }
        val ret =
"""
; CL[${egfClass}]    
; EV[${tournament.name}]
; PC[${tournament.country.lowercase()},${tournament.location}] 
; DT[${tournament.startDate},${tournament.endDate}]
; HA[${
    if (tournament.pairing.type == PairingType.MAC_MAHON) "h${tournament.pairing.pairingParams.handicap.correction}"
    else "h9"
    
}]
; KM[${tournament.komi}]
; TM[${tournament.timeSystem.adjustedTime() / 60}]
; CM[Generated by Pairgoth v0.1]
;
; Pl Name                            Rk Co Club  ${ criteria.map { it.name.replace(Regex("(S?)O?(SOS|DOS)[MW]?"), "$1$2").padStart(7, ' ') }.joinToString(" ") }
${
    lines.joinToString("\n") { player ->
        "${
            player.getString("num")!!.padStart(4, ' ')
        } ${
            "${player.getString("name")} ${player.getString("firstname")}".padEnd(30, ' ').take(30)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("country")!!.uppercase()
        } ${
            (player.getString("club") ?: "").padStart(4).take(4)
        } ${
            criteria.joinToString(" ") { numFormat.format(player.getDouble(it.name)!!).let { if (it.contains('.')) it else "$it  " }.padStart(7, ' ') }
        }  ${
            player.getArray("results")!!.map {
                (it as String).padStart(8, ' ')
            }.joinToString(" ")
        }"
    }
}
"""
        writer.println(ret)
    }

    private fun exportToFFGFormat(tournament: Tournament<*>, lines: List<Json.Object>, writer: PrintWriter) {
        // let's try in UTF-8
        val ret =
""";name=${tournament.shortName}
;date=${frDate.format(tournament.startDate)}
;vill=${tournament.location}${if (tournament.online) "(online)" else ""}
;comm=${tournament.name}
;prog=Pairgoth v0.1
;time=${tournament.timeSystem.mainTime / 60}
;ta=${tournament.timeSystem.adjustedTime() / 60}
;size=${tournament.gobanSize}
;komi=${tournament.komi}
;
;Num Nom Prenom               Niv Licence Club
${
    lines.joinToString("\n") { player ->
        "${
            player.getString("num")!!.padStart(4, ' ')
        } ${
            "${player.getString("name")} ${player.getString("firstname")}".padEnd(24, ' ').take(24)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("ffg") ?: "       "
        }   ${
            (player.getString("club") ?: "").padStart(6).take(6)
        } ${
            player.getArray("results")!!.joinToString(" ") {
                (it as String).replace("/", "").replace(Regex("(?<=[bw])$"), "0").padStart(7, ' ')
            }
        }"
    }
}
"""
        writer.println(ret)
    }

    private val numFormat = DecimalFormat("###0.#")
    private val frDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
