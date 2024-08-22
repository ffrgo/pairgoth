package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.Criterion.*
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.adjustedTime
import org.jeudego.pairgoth.model.displayRank
import java.io.PrintWriter
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.jeudego.pairgoth.model.TimeSystem.TimeSystemType.*
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.Event
import org.jeudego.pairgoth.server.WebappManager
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.*
import kotlin.collections.ArrayList

object StandingsHandler: PairgothApiHandler {
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: tournament.rounds
        val includePreliminary = request.getParameter("include_preliminary")?.let { it.toBoolean() } ?: false

        val sortedPairables = tournament.getSortedPairables(round, includePreliminary)
        tournament.populateFrozenStandings(sortedPairables, round)

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
                if (neededCriteria.first() == SCOREX) {
                    neededCriteria.add(1, MMS)
                }
                exportToEGFFormat(tournament, sortedPairables, neededCriteria, writer)
                writer.flush()
                return null
            }
            "application/ffg" -> {
                response.contentType = "text/plain;charset=${encoding}"
                exportToFFGFormat(tournament, sortedPairables, writer)
                writer.flush()
                return null
            }
            "text/csv" -> {
                response.contentType = "text/csv;charset=${encoding}"
                exportToCSVFormat(tournament, sortedPairables, writer)
                writer.flush()
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
            "${
                player.getString("name")?.toSnake(true)
                
            } ${
                player.getString("firstname")?.toSnake() ?: ""
            }".padEnd(30, ' ').take(30)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("country")?.uppercase() ?: ""
        } ${
            (player.getString("club") ?: "").toSnake().padStart(4).take(4)
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

    private fun String.toSnake(upper: Boolean = false): String {
        val sanitized = sanitizeISO()
        val parts = sanitized.trim().split(Regex("(?:\\s|\\xA0)+"))
        val snake = parts.joinToString("_") { part ->
            if (upper) part.uppercase(Locale.ROOT)
            else part.capitalize()
        }
        return snake
    }

    private fun String.sanitizeISO(): String {
        val ret = Normalizer.normalize(this, Normalizer.Form.NFD)
        return ret.replace(Regex("\\p{M}"), "")
            // some non accented letters give problems in ISO, there may be other
            .replace('Ð', 'D')
            .replace('ø', 'o')
    }

    private fun exportToFFGFormat(tournament: Tournament<*>, lines: List<Json.Object>, writer: PrintWriter) {
        val version = WebappManager.properties.getProperty("version")!!
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
; Generated by Pairgoth $version
; ${
    when (tournament.timeSystem.type) {
        CANADIAN -> "Canadian ${tournament.timeSystem.mainTime / 60} minutes, ${tournament.timeSystem.stones} stones / ${tournament.timeSystem.byoyomi / 60} minutes"
        JAPANESE -> "Japanese ${tournament.timeSystem.mainTime / 60} minutes, ${tournament.timeSystem.periods} periods of ${tournament.timeSystem.byoyomi / 60} minutes"
        FISCHER -> "Fisher ${tournament.timeSystem.mainTime / 60} minutes + ${tournament.timeSystem.increment} seconds${ if (tournament.timeSystem.maxTime > 0) ", max ${tournament.timeSystem.maxTime / 60} minutes" else "" }"
        SUDDEN_DEATH -> "Sudden death ${tournament.timeSystem.mainTime / 60} minutes"
    }
}
;
;Num Nom Prenom               Niv Licence Club
${
    lines.joinToString("\n") { player ->
        "${
            player.getString("num")!!.padStart(4, ' ')
        } ${
            "${player.getString("name")?.toSnake(true)} ${player.getString("firstname")?.toSnake() ?: ""}".padEnd(24, ' ').take(24)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("ffg") ?: "       "
        } ${
            if (player.getString("country") == "FR")
                (player.getString("club") ?: "").toSnake().padEnd(4).take(4)
            else
                (player.getString("country") ?: "").padEnd(4).take(4)
        } ${
            player.getArray("results")!!.joinToString(" ") {
                (it as String).replace(Regex("(?<=[bw])$"), "0").replace("0=", "0=   ").padStart(7, ' ')
            }
        }"
    }
}
"""
        writer.println(ret)
    }

    private fun exportToCSVFormat(tournament: Tournament<*>, lines: List<Json.Object>, writer: PrintWriter) {
        val fields = listOf(
            Pair("num", false),
            Pair("place", false),
            // Pair("egf", false), TODO configure display of egf / ffg field
            Pair("name", true),
            Pair("firstname", true),
            Pair("country", false),
            Pair("club", true),
            Pair("rank", false),
            Pair("NBW", false)
        );

        // headers
        writer.println("${
            fields.joinToString(";") { it.first }
        };${
            (1..tournament.rounds).joinToString(";") { "R$it" }
        };${
            tournament.pairing.placementParams.criteria.joinToString(";") { it.name }            
        }")

        // lines
        lines.forEach { line ->
            writer.println("${
                fields.joinToString(";") { if (it.second) "\"${line[it.first] ?: ""}\"" else "${line[it.first] ?: ""}" }
            };${
                line.getArray("results")!!.joinToString(";")
            };${
                tournament.pairing.placementParams.criteria.joinToString(";") { line.getString(it.name) ?: "" }
            }")
        }
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val sortedPairables = tournament.getSortedPairables(tournament.rounds)
        tournament.frozen = sortedPairables.toJsonArray()
        tournament.dispatchEvent(Event.TournamentUpdated, request, tournament.toJson())
        return Json.Object("status" to "ok")
    }

    private val numFormat = DecimalFormat("###0.#")
    private val frDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
