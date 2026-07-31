package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.historyBefore
import org.jeudego.pairgoth.pairing.DirectConfrontation
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import kotlin.math.max
import kotlin.math.min

fun Tournament<*>.getSortedPairables(round: Int, includePreliminary: Boolean = false): List<Json.Object> {

    // the frozen snapshot is the published final standings
    if (frozen != null && round == rounds) {
        return ArrayList(frozen!!.map { it -> it as Json.Object })
    }

    val history = historyHelper(round + 1)

    val neededCriteria = ArrayList(pairing.placementParams.criteria)
    if (!neededCriteria.contains(Criterion.NBW)) neededCriteria.add(Criterion.NBW)
    if (!neededCriteria.contains(Criterion.RATING)) neededCriteria.add(Criterion.RATING)
    // MacMahon ⇒ MMS present on every row, teams included: the standings view and the EGF
    // export insert an MMS column on their own (e.g. when SCOREX comes first)
    if (pairing.type == PairingType.MAC_MAHON && !neededCriteria.contains(Criterion.MMS)) neededCriteria.add(Criterion.MMS)
    val criteria = neededCriteria.map { crit ->
        crit.name to when (crit) {
            Criterion.NONE -> StandingsHandler.nullMap
            Criterion.CATEGORY -> StandingsHandler.nullMap
            Criterion.RANK -> pairables.mapValues { it.value.rank }
            Criterion.RATING -> pairables.mapValues { it.value.rating }
            Criterion.NBW -> history.wins
            Criterion.MMS -> history.mms
            Criterion.SCOREX -> history.scoresX
            Criterion.STS -> StandingsHandler.nullMap
            Criterion.CPS -> StandingsHandler.nullMap

            Criterion.SOSW -> history.sos
            Criterion.SOSWM1 -> history.sosm1
            Criterion.SOSWM2 -> history.sosm2
            Criterion.SODOSW -> history.sodos
            Criterion.SOSOSW -> history.sosos
            Criterion.CUSSW -> if (round == 0) StandingsHandler.nullMap else history.cumScore
            Criterion.SOSM -> history.sos
            Criterion.SOSMM1 -> history.sosm1
            Criterion.SOSMM2 -> history.sosm2
            Criterion.SODOSM -> history.sodos
            Criterion.SOSOSM -> history.sosos
            Criterion.CUSSM -> history.cumScore

            Criterion.SOSTS -> StandingsHandler.nullMap

            Criterion.EXT -> StandingsHandler.nullMap
            Criterion.EXR -> StandingsHandler.nullMap

            // group-relative, patched below once every other criterion value is known
            Criterion.SDC -> StandingsHandler.nullMap
            Criterion.DC -> StandingsHandler.nullMap
        }
    }
    val jsonPairables = pairables.values.filter { includePreliminary || it.final }.map { it.toDetailedJson() }
    jsonPairables.forEach { player ->
        for (crit in criteria) {
            player[crit.first] = crit.second[player.getID()] ?: 0.0
        }
        player["results"] = Json.MutableArray(List(round) { "0=" })
    }

    // direct confrontation: on each group tied on the criteria before DC/SDC,
    // rank by games between group members (ties broken by the criteria after)
    val placementCriteria = pairing.placementParams.criteria
    val dirIndex = placementCriteria.indexOfFirst { it == Criterion.DC || it == Criterion.SDC }
    if (dirIndex >= 0) {
        val before = placementCriteria.subList(0, dirIndex).map { it.name }
        val after = placementCriteria.drop(dirIndex + 1)
            .filter { it != Criterion.DC && it != Criterion.SDC }.map { it.name }
        val games = historyBefore(round + 1).flatten()
        val byId = jsonPairables.associateBy { it.getID()!! }
        jsonPairables.groupBy { p -> before.map { p.getDouble(it) ?: 0.0 } }.values.forEach { group ->
            val members = group.map { it.getID()!! }
            val wins = DirectConfrontation.netWins(games, members.toSet())
            val afterKey = { id: ID -> after.map { byId[id]!!.getDouble(it) ?: 0.0 } }
            if (placementCriteria.contains(Criterion.DC))
                DirectConfrontation.dc(members, wins, afterKey).forEach { (id, dc) -> byId[id]!![Criterion.DC.name] = dc }
            if (placementCriteria.contains(Criterion.SDC))
                DirectConfrontation.sdc(members, wins).forEach { (id, sdc) -> byId[id]!![Criterion.SDC.name] = sdc }
        }
    }

    val sortedPairables = jsonPairables.sortedWith { left, right ->
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
        placementCriteria.map { crit -> p.getDouble(crit.name) ?: 0.0 }
    }.forEach {
        it.value.forEach { p -> p["place"] = place }
        place += it.value.size
    }

    return sortedPairables
}

fun Tournament<*>.populateStandings(sortedEntries: List<Json.Object>, round: Int = rounds, individualStandings: Boolean) {
    val sortedMap = sortedEntries.associateBy {
        it.getID()!!
    }

    // refresh name, firstname, club and level
    val refMap = if (individualStandings) players else pairables
    sortedMap.forEach { (id, pairable) ->
        val mutable = pairable as Json.MutableObject
        refMap[id]?.let {
            mutable["name"] = it.name
            if (it is Player) {
                mutable["firstname"] = it.firstname
            }
            mutable["club"] = it.club
            mutable["rating"] = it.rating
            mutable["rank"] = it.rank
        }
    }

    // fill result
    for (r in 1..round) {
        val roundGames = if (individualStandings) individualGames(r) else games(r)
        roundGames.values.forEach { game ->
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
                    Game.Result.UNKNOWN -> "?"
                    Game.Result.BLACK, Game.Result.BOTHLOOSE -> "-"
                    Game.Result.WHITE, Game.Result.BOTHWIN -> "+"
                    Game.Result.JIGO, Game.Result.CANCELLED -> "="
                }
                val results = white.getArray("results") as Json.MutableArray
                results[r - 1] =
                    if (blackNum == 0) "0$mark"
                    else "$blackNum$mark/$whiteColor$handicap"
            }
            if (black != null) {
                val mark =  when (game.result) {
                    Game.Result.UNKNOWN -> "?"
                    Game.Result.BLACK, Game.Result.BOTHWIN -> "+"
                    Game.Result.WHITE, Game.Result.BOTHLOOSE -> "-"
                    Game.Result.JIGO, Game.Result.CANCELLED -> "="
                }
                val results = black.getArray("results") as Json.MutableArray
                results[r - 1] =
                    if (whiteNum == 0) "0$mark"
                    else "$whiteNum$mark/$blackColor$handicap"
            }
        }
    }
}

fun TeamTournament.getSortedTeamMembers(round: Int): List<Json.Object> {

    val teamGames = historyBefore(round + 1)
    val individualHistory = teamGames.map { roundTeamGames ->
        roundTeamGames.flatMap { game ->  individualGames[game.id]?.toList() ?: listOf() }
    }
    val historyHelper = HistoryHelper(individualHistory).apply {
        scoresFactory = { wins }
    }
    val neededCriteria = mutableListOf(Criterion.NBW, Criterion.RATING)
    val criteria = neededCriteria.map { crit ->
        crit.name to when (crit) {
            Criterion.NBW -> historyHelper.wins
            Criterion.RANK -> pairables.mapValues { it.value.rank }
            Criterion.RATING -> pairables.mapValues { it.value.rating }
            else -> null
        }
    }
    val jsonPlayers = players.values.filter { it.final }.map { it.toDetailedJson() }
    jsonPlayers.forEach { player ->
        for (crit in criteria) {
            player[crit.first] = crit.second?.get(player.getID()) ?: 0.0
        }
        player["results"] = Json.MutableArray(List(round) { "0=" })
    }
    val sortedPlayers = jsonPlayers.sortedWith { left, right ->
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
    sortedPlayers.groupBy { p ->
        Triple(
            criteria.getOrNull(0)?.first?.let { crit -> p.getDouble(crit) ?: 0.0 } ?: 0.0,
            criteria.getOrNull(1)?.first?.let { crit -> p.getDouble(crit) ?: 0.0 } ?: 0.0,
            criteria.getOrNull(2)?.first?.let { crit -> p.getDouble(crit) ?: 0.0 } ?: 0.0
        )
    }.forEach {
        it.value.forEach { p -> p["place"] = place }
        place += it.value.size
    }

    return sortedPlayers
}
