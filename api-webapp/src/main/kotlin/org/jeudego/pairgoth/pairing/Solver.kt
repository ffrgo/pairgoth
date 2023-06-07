package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.store.Store
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.builder.GraphBuilder
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


private fun detRandom(max: Long, p1: Pairable, p2: Pairable): Long {
    var nR: Long = 0
    var inverse = false

    val seed1 = p1.nameSeed()
    val seed2 = p2.nameSeed()

    var name1 = seed1
    var name2 = seed2
    if (name1 > name2) {
        name1 = name2.also { name2 = name1 }
        inverse = true
    }
    val s = name1 + name2
    for (i in s.indices) {
        val c = s[i]
        nR += (c.code * (i + 1)).toLong()
    }
    nR = nR * 1234567 % (max + 1)
    if (inverse) nR = max - nR
    return nR
}

private fun nonDetRandom(max: Long): Long {
    if (max == 0L) {
        return 0
    }
    val r = Math.random() * (max + 1)
    return r.toLong()
}

sealed class Solver(history: List<Game>, val pairables: List<Pairable>, val pairingParams: Pairing.PairingParams) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    open fun sort(p: Pairable, q: Pairable): Int = 0 // no sort by default
    open fun weight(p1: Pairable, p2: Pairable): Double {
        var score = 1L // 1 is minimum value because 0 means "no matching allowed"

        score += applyBaseCriteria(p1, p2)

        return score as Double
    }
    // The main criterion that will be used to define the groups should be defined by subclasses
    abstract fun mainCriterion(p1: Pairable): Int
    abstract fun mainCriterionMinMax(): Pair<Int, Int>

    // Base criteria
    open fun avoidDuplicatingGames(p1: Pairable, p2: Pairable): Long {
        if (historyHelper.playedTogether(p1, p2)) {
            return pairingParams.baseAvoidDuplGame
        } else {
            return 0
        }
    }

    open fun applyRandom(p1: Pairable, p2: Pairable): Long {
        if (pairingParams.baseDeterministic) {
            return detRandom(pairingParams.baseRandom, p1, p2)
        } else {
            return nonDetRandom(pairingParams.baseRandom)
        }
    }

    open fun applyBalanceBW(p1: Pairable, p2: Pairable): Long {
        // This cost is never applied if potential Handicap != 0
        // It is fully applied if wbBalance(sP1) and wbBalance(sP2) are strictly of different signs
        // It is half applied if one of wbBalance is 0 and the other is >=2
        val potentialHd: Int = handicap(p1, p2)
        if (potentialHd == 0) {
            val wb1: Int = p1.colorBalance
            val wb2: Int = p2.colorBalance
            if (wb1 * wb2 < 0) {
                return pairingParams.baseBalanceWB
            } else if (wb1 == 0 && abs(wb2) >= 2) {
                return pairingParams.baseBalanceWB / 2
            } else if (wb2 == 0 && abs(wb1) >= 2) {
                return pairingParams.baseBalanceWB / 2
            }
        }
        return 0
    }

    open fun applyBaseCriteria(p1: Pairable, p2: Pairable): Long {
        var score = 0L

        // Base Criterion 1 : Avoid Duplicating Game
        // Did p1 and p2 already play ?
        score += avoidDuplicatingGames(p1, p2)
        // Base Criterion 2 : Random
        score += applyRandom(p1, p2)
        // Base Criterion 3 : Balance W and B
        score += applyBalanceBW(p1, p2)

        return score
    }

    // Main criteria
    open fun applyMainCriteria(p1: Pairable, p2: Pairable): Long {
        var score = 0L;

        // Main criterion 1 avoid mixing category is moved to Swiss with category
        // TODO

        // Main criterion 2 minimize score difference
        score += minimizeScoreDifference(p1, p2)

        return score
    }

    open fun minimizeScoreDifference(p1: Pairable, p2: Pairable): Long {
        var scoCost: Long = 0
        val scoRange: Int = numberGroups
        // TODO check category equality if category are used in SwissCat
        val x = abs(groups[p1.id]!! - groups[p2.id]!!) as Double / scoRange.toDouble()
        val k: Double = pairingParams.standardNX1Factor
        scoCost = (pairingParams.mainMinimizeScoreDifference * (1.0 - x) * (1.0 + k * x)) as Long

        return scoCost
    }

    // Handicap functions
    open fun handicap(p1: Pairable, p2: Pairable): Int {
        var hd = 0
        var pseudoRank1: Int = p1.rank
        var pseudoRank2: Int = p2.rank

        pseudoRank1 = min(pseudoRank1, pairingParams.hd.noHdRankThreshold)
        pseudoRank2 = min(pseudoRank2, pairingParams.hd.noHdRankThreshold)
        hd = pseudoRank1 - pseudoRank2

        return clampHandicap(hd)
    }

    open fun clampHandicap(input_hd: Int): Int {
        var hd = input_hd
        if (hd > 0) {
            hd -= pairingParams.hd.correction
            hd = min(hd, 0)
        }
        if (hd < 0) {
            hd += pairingParams.hd.correction
            hd = max(hd, 0)
        }
        // Clamp handicap with ceiling
        hd = min(hd, pairingParams.hd.ceiling)
        hd = max(hd, -pairingParams.hd.ceiling)

        return hd
    }

    open fun games(black: Pairable, white: Pairable): List<Game> {
        // CB TODO team of individuals pairing
        return listOf(Game(id = Store.nextGameId, black = black.id, white = white.id, handicap = handicap(black, white)))
    }

    fun pair(): List<Game> {
        // check that at this stage, we have an even number of pairables
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleDirectedWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))
        for (i in sortedPairables.indices) {
            for (j in i + 1 until n) {
                val p = pairables[i]
                val q = pairables[j]
                weight(p, q).let { if (it != Double.NaN) builder.addEdge(p, q, it) }
                weight(q, p).let { if (it != Double.NaN) builder.addEdge(q, p, it) }
            }
        }
        val graph = builder.build()
        val matching = KolmogorovWeightedPerfectMatching(graph, ObjectiveSense.MINIMIZE)
        val solution = matching.matching

        val result = solution.flatMap {
            games(black = graph.getEdgeSource(it) , white = graph.getEdgeTarget(it))
        }
        return result
    }

    private fun computeGroups(): Pair<Map<Int, Int>, Int> {
        val (mainScoreMin, mainScoreMax) = mainCriterionMinMax()

        // TODO categories
        val groups: Map<Int, Int> = pairables.associate { pairable -> Pair(pairable.id, mainCriterion(pairable)) }

        return Pair(groups, mainScoreMax - mainScoreMin)
    }

    // Calculation parameters

    val n = pairables.size

    private val historyHelper =
        if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history)
        else HistoryHelper(history)

    private val groupsResult = computeGroups()
    private val groups = groupsResult.first
    private val numberGroups = groupsResult.second

    // pairables sorted using overloadable sort function
    private val sortedPairables by lazy {
        pairables.sortedWith(::sort)
    }

    // place (among sorted pairables)
    val Pairable.place: Int get() = _place[id]!!
    private val _place by lazy {
        sortedPairables.mapIndexed { index, pairable ->
            Pair(pairable.id, index)
        }.toMap()
    }

    // placeInGroup (of same score) : Pair(place, groupSize)
    val Pairable.placeInGroup: Pair<Int, Int> get() = _placeInGroup[id]!!
    private val _placeInGroup by lazy {
        sortedPairables.groupBy {
            it.score
        }.values.flatMap { group ->
            group.mapIndexed { index, pairable ->
                Pair(pairable.id, Pair(index, group.size))
            }
        }.toMap()
    }

    // already paired players map
    fun Pairable.played(other: Pairable) = historyHelper.playedTogether(this, other)

    // color balance (nw - nb)
    val Pairable.colorBalance: Int get() = historyHelper.colorBalance(this) ?: 0

    // score (number of wins)
    val Pairable.score: Double get() = historyHelper.score(this) ?: 0.0

    // sos
    val Pairable.sos: Double get() = historyHelper.sos(this) ?: 0.0

    // sosos
    val Pairable.sosos: Double get() = historyHelper.sosos(this) ?: 0.0

    // sodos
    val Pairable.sodos: Double get() = historyHelper.sodos(this) ?: 0.0
}
