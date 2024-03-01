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
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat

object StandingsHandler: PairgothApiHandler {
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid round number")

        val sortedPairables = tournament.getSortedPairables(round)
        val sortedMap = sortedPairables.associateBy {
            it.getID()!!
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
        val acceptHeader = request.getHeader("Accept") as String?
        val accept = acceptHeader?.substringBefore(";")
        val acceptEncoding = acceptHeader?.substringAfter(";charset=", "utf-8") ?: "utf-8"
        val encoding = when (acceptEncoding) {
            "utf-8" -> StandardCharsets.UTF_8
            "iso-8859-1" -> StandardCharsets.ISO_8859_1
            else -> ApiHandler.badRequest("unknown encoding in Accept header: $accept")
        }
        val writer by lazy {
            PrintWriter(OutputStreamWriter(response.outputStream, encoding))
        }
        return when (accept) {
            "application/json" -> sortedPairables.toJsonArray()
            "application/egf" -> {
                response.contentType = "text/plain;charset=${encoding}"
                val neededCriteria = ArrayList(tournament.pairing.placementParams.criteria)
                if (!neededCriteria.contains(NBW)) neededCriteria.add(NBW)
                exportToEGFFormat(tournament, sortedPairables, neededCriteria, writer)
                return null
            }
            "application/ffg" -> {
                response.contentType = "text/plain;charset=${encoding}"
                exportToFFGFormat(tournament, sortedPairables, writer)
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
