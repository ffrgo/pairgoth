package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.Criterion.*
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.TeamTournament
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

object StandingsHandler: PairgothApiHandler {
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: tournament.rounds
        val includePreliminary = request.getParameter("include_preliminary")?.toBoolean() ?: false

        val individualStandings = tournament is TeamTournament &&
                tournament.type.individual &&
                request.getParameter("individual_standings")?.toBoolean() == true

        val sortedEntries = if (individualStandings) {
            tournament.getSortedTeamMembers(round)
        } else {
            tournament.getSortedPairables(round, includePreliminary)
        }
        tournament.populateStandings(sortedEntries, round, individualStandings)

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
        val canonicalName = canonicalFileBaseName(tournament)
        return when (accept) {
            "application/json" -> sortedEntries.toJsonArray()
            "application/egf" -> {
                response.contentType = "text/plain;charset=${encoding}"
                val ext = if (tournament.pairing.type == PairingType.MAC_MAHON)
                    "h${tournament.pairing.pairingParams.handicap.correction}"
                else "h9"
                response.setHeader("Content-Disposition", "attachment; filename=\"$canonicalName.$ext\"")
                val neededCriteria = ArrayList(tournament.pairing.placementParams.criteria)
                if (!neededCriteria.contains(NBW)) neededCriteria.add(NBW)
                if (neededCriteria.first() == SCOREX) {
                    neededCriteria.add(1, MMS)
                }
                exportToEGFFormat(tournament, sortedEntries, neededCriteria, writer)
                writer.flush()
                return null
            }
            "application/ffg" -> {
                response.contentType = "text/plain;charset=${encoding}"
                response.setHeader("Content-Disposition", "attachment; filename=\"$canonicalName.tou\"")
                exportToFFGFormat(tournament, sortedEntries, writer)
                writer.flush()
                return null
            }
            "text/csv" -> {
                response.contentType = "text/csv;charset=${encoding}"
                exportToCSVFormat(tournament, sortedEntries, writer)
                writer.flush()
                return null
            }
            else -> ApiHandler.badRequest("invalid Accept header: $accept")
        }
    }

    private fun canonicalFileBaseName(tournament: Tournament<*>) =
        "${ffgDate.format(tournament.startDate)}-${tournament.location.lowercase(Locale.ROOT).toAscii()}"

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
; PC[${tournament.country.uppercase()},${tournament.location}] 
; DT[${tournament.startDate},${tournament.endDate}]
; HA[${
    if (tournament.pairing.type == PairingType.MAC_MAHON) "h${tournament.pairing.pairingParams.handicap.correction}"
    else "h9"
    
}]
; KM[${tournament.komi}]
; TM[${tournament.timeSystem.adjustedTime() / 60}]
; CM[Generated by Pairgoth ${WebappManager.properties.getProperty("version")}]
; CM[${timeSystemComment(tournament)}]
;
; Pl Name                            Rk Co Club  ${ criteria.map { it.name.replace(Regex("(S?)O?(SOS|DOS)[MW]?"), "$1$2").padStart(7, ' ') }.joinToString(" ") }
${
    lines.joinToString("\n") { player ->
        "${
            player.getString("num")!!.padStart(4, ' ')
        } ${
            "${
                player.getString("name")?.joinNameParts() ?: ""
            } ${
                player.getString("firstname")?.joinNameParts() ?: ""
            }".padEnd(30, ' ').take(30)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("country")?.uppercase() ?: ""
        } ${
            (player.getString("club") ?: "").padStart(4).take(4)
        } ${
            criteria.joinToString(" ") { numFormat.format(player.getDouble(it.name)!!).let { if (it.contains('.')) it else "$it  " }.padStart(7, ' ') }
        }  ${
            player.getArray("results")!!.map {
                (it as String).padStart(8, ' ')
            }.joinToString(" ")
        }${
            player.getString("egf")?.let { if (it.length == 8) " |$it" else "" } ?: ""
        }"
    }
}
"""
        writer.println(ret)
    }

    private fun timeSystemComment(tournament: Tournament<*>): String {
        val ts = tournament.timeSystem
        val mainMin = ts.mainTime / 60
        fun seconds(s: Int) = if (s >= 60 && s % 60 == 0) "${s / 60} minutes" else "$s seconds"
        return when (ts.type) {
            CANADIAN -> if (ts.byoyomi > 0 && ts.stones > 0) "Canadian $mainMin minutes, ${ts.stones} stones / ${seconds(ts.byoyomi)}"
                else "Sudden death $mainMin minutes"
            JAPANESE -> if (ts.byoyomi > 0 && ts.periods > 0) "Japanese $mainMin minutes, ${ts.periods} periods of ${seconds(ts.byoyomi)}"
                else "Sudden death $mainMin minutes"
            FISCHER -> if (ts.increment > 0) "Fischer $mainMin minutes + ${ts.increment} seconds${ if (ts.maxTime > 0) ", max ${ts.maxTime / 60} minutes" else "" }"
                else "Sudden death $mainMin minutes"
            SUDDEN_DEATH -> "Sudden death $mainMin minutes"
        }
    }

    // Word-split on whitespace, NBSP, and underscore (legacy stored form may use '_').
    private fun String.nameWords() =
        trim().split(Regex("(?:\\s|\\xA0|_)+")).filter { it.isNotEmpty() }

    // Storage-as-is, word-joined with '_' (FFG/EGF convention: no spaces in name/firstname).
    // Case is whatever storage holds; rely on PlayerHandler input canonicalisation to keep
    // it Title_Case in steady state. Legacy raw storage is exported as-is.
    private fun String.joinNameParts() = nameWords().joinToString("_")

    // For .tou (FFG) surname: spec wants UPPERCASE regardless of stored case.
    private fun String.toFFGSurname() = nameWords().joinToString("_") { it.uppercase(Locale.ROOT) }

    // Per-char encoding fallback: keep chars representable in the target charset,
    // otherwise NFD-strip diacritics and keep only the ASCII base.
    private fun String.encodeFallback(charset: java.nio.charset.Charset): String {
        val enc = charset.newEncoder()
        return buildString {
            this@encodeFallback.forEach { c ->
                if (enc.canEncode(c)) append(c)
                else append(Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { it.code < 128 })
            }
        }.replace('Ð', 'D').replace('ø', 'o')
    }

    // Strip diacritics and force ASCII (for filenames and other ASCII-required contexts).
    private fun String.toAscii(): String {
        val ret = Normalizer.normalize(this, Normalizer.Form.NFD)
        return ret.replace(Regex("\\p{M}"), "")
            .replace('Ð', 'D')
            .replace('ø', 'o')
    }

    private fun exportToFFGFormat(tournament: Tournament<*>, lines: List<Json.Object>, writer: PrintWriter) {
        val version = WebappManager.properties.getProperty("version")!!
        val ffgName = canonicalFileBaseName(tournament)
        val ret =
""";name=$ffgName
;date=${frDate.format(tournament.startDate)}
;vill=${tournament.location}${if (tournament.online) "(online)" else ""}
;comm=${tournament.name}
;prog=Pairgoth $version
;time=${tournament.timeSystem.mainTime / 60}
;ta=${tournament.timeSystem.adjustedTime() / 60}
;size=${tournament.gobanSize}
;komi=${tournament.komi}
; Generated by Pairgoth $version
; ${timeSystemComment(tournament)}
;
;Num Nom Prenom               Niv Licence Club
${
    lines.joinToString("\n") { player ->
        "${
            player.getString("num")!!.padStart(4, ' ')
        } ${
            "${(player.getString("name")?.toFFGSurname() ?: "").encodeFallback(StandardCharsets.ISO_8859_1)} ${(player.getString("firstname")?.joinNameParts() ?: "").encodeFallback(StandardCharsets.ISO_8859_1)}".padEnd(24, ' ').take(24)
        } ${
            displayRank(player.getInt("rank")!!).uppercase().padStart(3, ' ')
        } ${
            player.getString("ffg") ?: "       "
        } ${
            if (player.getString("country") == "FR")
                (player.getString("club") ?: "").padEnd(4).take(4)
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
    private val ffgDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
}
