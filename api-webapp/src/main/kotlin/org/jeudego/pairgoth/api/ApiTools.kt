package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
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
import kotlin.math.round

//  TODO CB avoid code redundancy with solvers

fun Tournament<*>.getSortedPairables(round: Int, includePreliminary: Boolean = false): List<Json.Object> {

    fun Pairable.mmBase(): Double {
        if (pairing !is MacMahon) throw Error("invalid call: tournament is not Mac Mahon")
        return min(max(rank, pairing.mmFloor), pairing.mmBar) + MacMahonSolver.mmsZero + mmsCorrection
    }

    fun roundScore(score: Double): Double {
        val epsilon = 0.00001
        // Note: this works for now because we only have .0 and .5 fractional parts
        return if (pairing.pairingParams.main.roundDownScore) floor(score + epsilon)
        else round(2 * score) / 2
    }

    if (frozen != null) {
        return ArrayList(frozen!!.map { it -> it as Json.Object })
    }

    // CB TODO - factorize history helper creation between here and solver classes
    val historyHelper = HistoryHelper(historyBefore(round + 1)) {
        if (pairing.type == PairingType.SWISS) {
            pairables.mapValues {
                Pair(0.0, wins[it.key] ?: 0.0)
            }
        }
        else {
            pairables.mapValues {
                it.value.let { pairable ->
                    val mmBase = pairable.mmBase()
                    val score = roundScore(mmBase +
                            (nbW(pairable) ?: 0.0) +
                            (1..round).map { round ->
                                if (playersPerRound.getOrNull(round - 1)?.contains(pairable.id) == true) 0.0 else 1.0
                            }.sum() * pairing.pairingParams.main.mmsValueAbsent)
                    Pair(
                        if (pairing.pairingParams.main.sosValueAbsentUseBase) mmBase
                        else roundScore(mmBase + round/2),
                        score
                    )
                }
            }
        }
    }
    val neededCriteria = ArrayList(pairing.placementParams.criteria)
    if (!neededCriteria.contains(Criterion.NBW)) neededCriteria.add(Criterion.NBW)
    if (!neededCriteria.contains(Criterion.RATING)) neededCriteria.add(Criterion.RATING)
    if (type == Tournament.Type.INDIVIDUAL && pairing.type == PairingType.MAC_MAHON && !neededCriteria.contains(Criterion.MMS)) neededCriteria.add(Criterion.MMS)
    val criteria = neededCriteria.map { crit ->
        crit.name to when (crit) {
            Criterion.NONE -> StandingsHandler.nullMap
            Criterion.CATEGORY -> StandingsHandler.nullMap
            Criterion.RANK -> pairables.mapValues { it.value.rank }
            Criterion.RATING -> pairables.mapValues { it.value.rating }
            Criterion.NBW -> historyHelper.wins
            Criterion.MMS -> historyHelper.mms
            Criterion.SCOREX -> historyHelper.scoresX
            Criterion.STS -> StandingsHandler.nullMap
            Criterion.CPS -> StandingsHandler.nullMap

            Criterion.SOSW -> historyHelper.sos
            Criterion.SOSWM1 -> historyHelper.sosm1
            Criterion.SOSWM2 -> historyHelper.sosm2
            Criterion.SODOSW -> historyHelper.sodos
            Criterion.SOSOSW -> historyHelper.sosos
            Criterion.CUSSW -> if (round == 0) StandingsHandler.nullMap else historyHelper.cumScore
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
    val pairables = pairables.values.filter { includePreliminary || it.final }.map { it.toDetailedJson() }
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

fun Tournament<*>.populateFrozenStandings(sortedPairables: List<Json.Object>, round: Int = rounds) {
    val sortedMap = sortedPairables.associateBy {
        it.getID()!!
    }

    // refresh name, firstname, club and level
    sortedMap.forEach { (id, player) ->
        val mutable = player as Json.MutableObject
        val live = players[id]!!
        mutable["name"] = live.name
        mutable["firstname"] = live.firstname
        mutable["club"] = live.club
        mutable["rating"] = live.rating
        mutable["rank"] = live.rank
    }

    // fill result
    for (r in 1..round) {
        games(r).values.forEach { game ->
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
