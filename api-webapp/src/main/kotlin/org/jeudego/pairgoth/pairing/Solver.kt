package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
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

sealed class Solver(
        val round: Int,
        history: List<Game>,
        val pairables: List<Pairable>,
        val pairingParams: Pairing.PairingParams,
        val placementParams: PlacementParams) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    open fun sort(p: Pairable, q: Pairable): Int {
        for (criterion in placementParams.criteria) {
            val criterionP = getCriterionValue(p, criterion)
            val criterionQ = getCriterionValue(q, criterion)
            if (criterionP != criterionQ) {
                return criterionP - criterionQ
            }
        }
        return 0
    }
    open fun weight(p1: Pairable, p2: Pairable): Double {
        var score = 1L // 1 is minimum value because 0 means "no matching allowed"

        score += applyBaseCriteria(p1, p2)

        score += applyMainCriteria(p1, p2)

        return score as Double
    }
    // The main criterion that will be used to define the groups should be defined by subclasses
    abstract fun mainCriterion(p1: Pairable): Int
    abstract fun mainCriterionMinMax(): Pair<Int, Int>
    // SOS and variants will be computed based on this score
    abstract fun computeStandingScore(): Map<ID, Double>
    // This function needs to be overridden for criterion specific to the current pairing mode
    open fun getSpecificCriterionValue(p1: Pairable, criterion: PlacementCriterion): Int {
        return -1
    }

    private fun getCriterionValue(p1: Pairable, criterion: PlacementCriterion): Int {
        val genericCritVal = historyHelper.getCriterionValue(p1, criterion)
        // If the value from the history helper is > 0 it means that it is a generic criterion
        // Just returns the value
        if (genericCritVal != -1) {
            return genericCritVal
        }
        // Otherwise we have to delegate it to the solver
        val critVal = getSpecificCriterionValue(p1, criterion)
        if (critVal == -1) throw Error("Couldn't compute criterion value")
        return critVal
    }

    fun pair(): List<Game> {
        // check that at this stage, we have an even number of pairables
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleDirectedWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))
        for (i in sortedPairables.indices) {
            for (j in i + 1 until pairables.size) {
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

        // Main criterion 3 If different groups, make a directed Draw-up/Draw-down
        // TODO

        // Main criterion 4 seeding
        score += applySeeding(p1, p2)

        return score
    }

    // Weight score computation details
    // Base criteria
    open fun avoidDuplicatingGames(p1: Pairable, p2: Pairable): Long {
        if (p1.played(p2)) {
            return 0 // We get no score if pairables already played together
        } else {
            return pairingParams.baseAvoidDuplGame
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

    open fun minimizeScoreDifference(p1: Pairable, p2: Pairable): Long {
        var score = 0L
        val scoreRange: Int = numberGroups
        // TODO check category equality if category are used in SwissCat
        val x = abs(p1.group - p2.group) as Double / scoreRange.toDouble()
        val k: Double = pairingParams.standardNX1Factor
        score = (pairingParams.mainMinimizeScoreDifference * (1.0 - x) * (1.0 + k * x)) as Long

        return score
    }

    fun applySeeding(p1: Pairable, p2: Pairable): Long {
        var score = 0L
        // Apply seeding for players in the same group
        if (p1.group == p2.group) {
            val (cla1, groupSize) = p1.placeInGroup
            val cla2 = p2.placeInGroup.first
            val maxSeedingWeight = pairingParams.maMaximizeSeeding

            val currentSeedSystem: SeedMethod = if (round <= pairingParams.maLastRoundForSeedSystem1)
                pairingParams.maSeedSystem1 else pairingParams.maSeedSystem2

            score += when(currentSeedSystem) {
                // The best is to get 2 * |Cla1 - Cla2| - groupSize    close to 0
                SeedMethod.SPLIT_AND_SLIP -> {
                    val x = 2 * abs(cla1 - cla2) - groupSize
                    maxSeedingWeight - maxSeedingWeight * x / groupSize * x / groupSize
                }

                // The best is to get cla1 + cla2 - (groupSize - 1) close to 0
                SeedMethod.SPLIT_AND_FOLD -> {
                    val x = cla1 + cla2 - (groupSize - 1)
                    maxSeedingWeight - maxSeedingWeight * x / (groupSize - 1) * x / (groupSize - 1)
                }

                SeedMethod.SPLIT_AND_RANDOM -> {
                    if ((2 * cla1 < groupSize && 2 * cla2 >= groupSize) || (2 * cla1 >= groupSize && 2 * cla2 < groupSize)) {
                        val randRange = (maxSeedingWeight * 0.2).toLong()
                        val rand = detRandom(randRange, p1, p2)
                        maxSeedingWeight - rand
                    } else {
                        0L
                    }
                }
            }
        }
        return score
    }

    // Handicap functions
    // Has to be overridden if handicap is not based on rank
    open fun handicap(p1: Pairable, p2: Pairable): Int {
        var hd = 0
        var pseudoRank1: Int = p1.rank
        var pseudoRank2: Int = p2.rank

        pseudoRank1 = min(pseudoRank1, pairingParams.hd.noHdRankThreshold)
        pseudoRank2 = min(pseudoRank2, pairingParams.hd.noHdRankThreshold)
        hd = pseudoRank1 - pseudoRank2

        return clampHandicap(hd)
    }

    open fun clampHandicap(inputHd: Int): Int {
        var hd = inputHd
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

    // Generic parameters calculation
    private val standingScore = computeStandingScore()
    val historyHelper =
            if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history, standingScore)
            else HistoryHelper(history, standingScore)



    // Decide each pairable group based on the main criterion
    private val numberGroups by lazy {
        val (mainScoreMin, mainScoreMax) = mainCriterionMinMax()
        mainScoreMax - mainScoreMin
    }
    private val _groups = pairables.associate { pairable -> Pair(pairable.id, mainCriterion(pairable)) }

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
    private val Pairable.placeInGroup: Pair<Int, Int> get() = _placeInGroup[id]!!
    private val _placeInGroup by lazy {
        sortedPairables.groupBy {
            it.group
        }.values.flatMap { group ->
            group.mapIndexed { index, pairable ->
                Pair(pairable.id, Pair(index, group.size))
            }
        }.toMap()
    }

    // already paired players map
    private fun Pairable.played(other: Pairable) = historyHelper.playedTogether(this, other)

    // color balance (nw - nb)
    private val Pairable.colorBalance: Int get() = historyHelper.colorBalance(this) ?: 0

    private val Pairable.group: Int get() = _groups[id]!!

    // score (number of wins)
    val Pairable.nbW: Double get() = historyHelper.nbW(this) ?: 0.0

    val Pairable.sos: Double get() = historyHelper.sos[id]!!

    val Pairable.sosm1: Double get() = historyHelper.sosm1[id]!!
    val Pairable.sosm2: Double get() = historyHelper.sosm2[id]!!
    val Pairable.sosos: Double get() = historyHelper.sosos[id]!!
    val Pairable.sodos: Double get() = historyHelper.sodos[id]!!
    val Pairable.cums: Double get() = historyHelper.cumscore[id]!!




}
