package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.MainCritParams.SeedMethod.*
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

val DEBUG_EXPORT_WEIGHT = true

private fun detRandom(max: Double, p1: Pairable, p2: Pairable): Double {
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
    var nR = 0.0
    for (i in s.indices) {
        val c = s[i]
        nR += (c.code * (i + 1)).toDouble()
    }
    nR = nR * 1234567 % (max + 1)
    if (inverse) nR = max - nR
    return nR
}

private fun nonDetRandom(max: Double) =
    if (max == 0.0) 0.0
    else Math.random() * (max + 1.0)

sealed class Solver(
        val round: Int,
        history: List<Game>,
        val pairables: List<Pairable>,
        val pairingParams: PairingParams,
        val placementParams: PlacementParams) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    open fun sort(p: Pairable, q: Pairable): Int {
        for (criterion in placementParams.criteria) {
            val criterionP = getCriterionValue(p, criterion)
            val criterionQ = getCriterionValue(q, criterion)
            if (criterionP != criterionQ) {
                return (criterionP - criterionQ).toInt()
            }
        }
        return 0
    }
    open fun weight(p1: Pairable, p2: Pairable) =
        1.0 + // 1 is minimum value because 0 means "no matching allowed"
        applyBaseCriteria(p1, p2) +
        applyMainCriteria(p1, p2) +
        applySecondaryCriteria(p1, p2)

    // The main criterion that will be used to define the groups should be defined by subclasses
    abstract fun mainCriterion(p1: Pairable): Int
    abstract fun mainCriterionMinMax(): Pair<Int, Int>
    // SOS and variants will be computed based on this score
    abstract fun computeStandingScore(): Map<ID, Double>
    // This function needs to be overridden for criterion specific to the current pairing mode
    open fun getSpecificCriterionValue(p1: Pairable, criterion: Criterion): Double {
        return -1.0
    }

    private fun getCriterionValue(p1: Pairable, criterion: Criterion): Double {
        val genericCritVal = historyHelper.getCriterionValue(p1, criterion)
        // If the value from the history helper is > 0 it means that it is a generic criterion
        // Just returns the value
        if (genericCritVal < 0) {
            return genericCritVal
        }
        // Otherwise we have to delegate it to the solver
        val critVal = getSpecificCriterionValue(p1, criterion)
        if (critVal < 0) throw Error("Couldn't compute criterion value")
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

    open fun applyBaseCriteria(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

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
    open fun applyMainCriteria(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

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

    open fun applySecondaryCriteria(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // See Swiss with category for minimizing handicap criterion

        // TODO understand where opengotha test if need to be applied

        // Geographical criterion
        score += avoidSameGeo(p1, p2)

        return score
    }

    // Weight score computation details
    // Base criteria
    open fun avoidDuplicatingGames(p1: Pairable, p2: Pairable): Double {
        if (p1.played(p2)) {
            return 0.0 // We get no score if pairables already played together
        } else {
            return pairingParams.base.dupWeight
        }
    }

    open fun applyRandom(p1: Pairable, p2: Pairable): Double {
        if (pairingParams.base.deterministic) {
            return detRandom(pairingParams.base.random, p1, p2)
        } else {
            return nonDetRandom(pairingParams.base.random)
        }
    }

    open fun applyBalanceBW(p1: Pairable, p2: Pairable): Double {
        // This cost is never applied if potential Handicap != 0
        // It is fully applied if wbBalance(sP1) and wbBalance(sP2) are strictly of different signs
        // It is half applied if one of wbBalance is 0 and the other is >=2
        val potentialHd: Int = handicap(p1, p2)
        if (potentialHd == 0) {
            val wb1: Int = p1.colorBalance
            val wb2: Int = p2.colorBalance
            if (wb1 * wb2 < 0) {
                return pairingParams.base.colorBalance
            } else if (wb1 == 0 && abs(wb2) >= 2) {
                return pairingParams.base.colorBalance / 2
            } else if (wb2 == 0 && abs(wb1) >= 2) {
                return pairingParams.base.colorBalance / 2
            }
        }
        return 0.0
    }

    open fun minimizeScoreDifference(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        val scoreRange: Int = numberGroups
        // TODO check category equality if category are used in SwissCat
        val x = abs(p1.group - p2.group) as Double / scoreRange.toDouble()
        val k: Double = pairingParams.base.nx1
        score = pairingParams.main.scoreWeight * (1.0 - x) * (1.0 + k * x)

        return score
    }

    fun applySeeding(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // Apply seeding for players in the same group
        if (p1.group == p2.group) {
            val (cla1, groupSize) = p1.placeInGroup
            val cla2 = p2.placeInGroup.first
            val maxSeedingWeight = pairingParams.main.seedingWeight

            val currentSeedSystem: MainCritParams.SeedMethod = if (round <= pairingParams.main.lastRoundForSeedSystem1)
                pairingParams.main.seedSystem1 else pairingParams.main.seedSystem2

            score += when(currentSeedSystem) {
                // The best is to get 2 * |Cla1 - Cla2| - groupSize    close to 0
                SPLIT_AND_SLIP -> {
                    val x = 2.0 * abs(cla1 - cla2) - groupSize
                    maxSeedingWeight - maxSeedingWeight * x / groupSize * x / groupSize
                }

                // The best is to get cla1 + cla2 - (groupSize - 1) close to 0
                SPLIT_AND_FOLD -> {
                    val x = cla1 + cla2 - (groupSize - 1)
                    maxSeedingWeight - maxSeedingWeight * x / (groupSize - 1) * x / (groupSize - 1)
                }

                SPLIT_AND_RANDOM -> {
                    if ((2 * cla1 < groupSize && 2 * cla2 >= groupSize) || (2 * cla1 >= groupSize && 2 * cla2 < groupSize)) {
                        val randRange = maxSeedingWeight * 0.2
                        val rand = detRandom(randRange, p1, p2)
                        maxSeedingWeight - rand
                    } else {
                        0.0
                    }
                }
            }
        }
        return score
    }

    open fun doNeedToApplySecondaryCriteria(p1: Pairable, p2: Pairable) {
        // secCase = 0 : No player is above thresholds
        // secCase = 1 : One player is above thresholds
        // secCase = 2 : Both players are above thresholds
        // TODO understand where it is used
    }

    fun avoidSameGeo(p1: Pairable, p2: Pairable): Double {
        val placementScoreRange = numberGroups

        val geoMaxCost = pairingParams.geo.avoidSameGeo

        val countryFactor = pairingParams.geo.preferMMSDiffRatherThanSameCountry
        val clubFactor: Int = pairingParams.geo.preferMMSDiffRatherThanSameClub
        //val groupFactor: Int = pairingParams.geo.preferMMSDiffRatherThanSameClubsGroup

        // Same country
        val countryRatio = if (p1.country != p2.country && countryFactor != 0) {
            min(countryFactor.toDouble() / placementScoreRange as Double, 1.0) // clamp to 1
        } else {
            0.0
        }

        // Same club and club group (TODO club group)
        var clubRatio = 0.0
        val commonClub = p1.club == p2.club
        val commonGroup = false // TODO

        if (commonGroup && !commonClub) {
            clubRatio = if (clubFactor == 0) {
                0.0
            } else {
                clubFactor.toDouble() / 2.0 / placementScoreRange as Double
            }

        } else if (!commonGroup && !commonClub) {
            clubRatio = if (clubFactor == 0) {
                0.0
            } else {
                clubFactor.toDouble() * 1.2 / placementScoreRange as Double
            }
        }
        clubRatio = min(clubRatio, 1.0)

        // TODO Same family

        // compute geoRatio

        val mainPart = max(countryRatio, clubRatio)
        val secPart = min(countryRatio, clubRatio)

        var geoRatio = mainPart + secPart / 2.0
        if (geoRatio > 0.0) {
            geoRatio += 0.5 / placementScoreRange as Double
        }

        // The concavity function is applied to geoRatio to get geoCost
        val dbGeoCost: Double = geoMaxCost.toDouble() * (1.0 - geoRatio) * (1.0 + pairingParams.base.nx1 * geoRatio)
        var score = pairingParams.main.scoreWeight - dbGeoCost
        score = min(score, geoMaxCost)

        return score
    }

    // Handicap functions
    // Has to be overridden if handicap is not based on rank
    open fun handicap(p1: Pairable, p2: Pairable): Int {
        var hd = 0
        var pseudoRank1: Int = p1.rank
        var pseudoRank2: Int = p2.rank

        pseudoRank1 = min(pseudoRank1, pairingParams.handicap.rankThreshold)
        pseudoRank2 = min(pseudoRank2, pairingParams.handicap.rankThreshold)
        hd = pseudoRank1 - pseudoRank2

        return clampHandicap(hd)
    }

    open fun clampHandicap(inputHd: Int): Int {
        var hd = inputHd
        if (hd > 0) {
            hd -= pairingParams.handicap.correction
            hd = min(hd, 0)
        }
        if (hd < 0) {
            hd += pairingParams.handicap.correction
            hd = max(hd, 0)
        }
        // Clamp handicap with ceiling
        hd = min(hd, pairingParams.handicap.ceiling)
        hd = max(hd, -pairingParams.handicap.ceiling)

        return hd
    }

    open fun games(black: Pairable, white: Pairable): List<Game> {
        // CB TODO team of individuals pairing
        return listOf(Game(id = Store.nextGameId, black = black.id, white = white.id, handicap = handicap(black, white)))
    }

    // Generic parameters calculation
    //private val standingScore by lazy { computeStandingScore() }

    val historyHelper = if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history, ::computeStandingScore)
    else HistoryHelper(history, ::computeStandingScore)



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
