package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.Criterion.*
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
    var name1 = p1.nameSeed()
    var name2 = p2.nameSeed()
    if (name1 > name2) {
        name1 = name2.also { name2 = name1 }
        inverse = true
    }
    var nR = "$name1$name2".mapIndexed { i, c ->
        c.code.toDouble() * (i + 1)
    }.sum() * 1234567 % (max + 1)
    if (inverse) nR = max - nR
    return nR
}

private fun nonDetRandom(max: Double) =
    if (max == 0.0) 0.0
    else Math.random() * (max + 1.0)

sealed class Solver(
    val round: Int,
    history: List<List<Game>>,
    val pairables: List<Pairable>,
    val pairing: PairingParams,
    val placement: PlacementParams
    ) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    abstract val scores: Map<ID, Double>
    val historyHelper by lazy {
        if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history, scores)
        else HistoryHelper(history, scores)
    }

    // pairables sorted using overloadable sort function
    private val sortedPairables by lazy {
        pairables.sortedWith(::sort)
    }

    protected val pairablesMap by lazy {
        pairables.associateBy { it.id }
    }

    open fun sort(p: Pairable, q: Pairable): Int {
        for (criterion in placement.criteria) {
            val criterionP = p.eval(criterion)
            val criterionQ = q.eval(criterion)
            if (criterionP != criterionQ) {
                return (criterionP * 100 - criterionQ * 100).toInt()
            }
        }
        return 0
    }
    open fun weight(p1: Pairable, p2: Pairable) =
        1.0 + // 1 is minimum value because 0 means "no matching allowed"
        pairing.base.apply(p1, p2) +
        pairing.main.apply(p1, p2) +
        pairing.secondary.apply(p1, p2) +
        pairing.geo.apply(p1, p2)

    // The main criterion that will be used to define the groups should be defined by subclasses
    val Pairable.main: Double get() = scores[id] ?: 0.0
    abstract val mainLimits: Pair<Double, Double>
    // SOS and variants will be computed based on this score
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

    // base criteria

    open fun BaseCritParams.apply(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // Base Criterion 1 : Avoid Duplicating Game
        // Did p1 and p2 already play ?
        score += avoidDuplicatingGames(p1, p2)
        // Base Criterion 2 : Random
        score += applyRandom(p1, p2)
        // Base Criterion 3 : Balance W and B
        score += applyColorBalance(p1, p2)

        return score
    }

    // Weight score computation details
    open fun  BaseCritParams.avoidDuplicatingGames(p1: Pairable, p2: Pairable): Double {
        return if (p1.played(p2)) 0.0 // We get no score if pairables already played together
        else dupWeight
    }

    open fun BaseCritParams.applyRandom(p1: Pairable, p2: Pairable): Double {
        return if (deterministic) detRandom(random, p1, p2)
        else nonDetRandom(random)
    }

    open fun BaseCritParams.applyColorBalance(p1: Pairable, p2: Pairable): Double {
        // This cost is never applied if potential Handicap != 0
        // It is fully applied if wbBalance(sP1) and wbBalance(sP2) are strictly of different signs
        // It is half applied if one of wbBalance is 0 and the other is >=2
        val potentialHd: Int = pairing.handicap.handicap(p1, p2)
        if (potentialHd == 0) {
            val wb1: Int = p1.colorBalance
            val wb2: Int = p2.colorBalance
            if (wb1 * wb2 < 0) return colorBalance
            else if (wb1 == 0 && abs(wb2) >= 2 || wb2 == 0 && abs(wb1) >= 2) return colorBalance / 2
        }
        return 0.0
    }

    // Main criteria
    open fun MainCritParams.apply(p1: Pairable, p2: Pairable): Double {
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

    open fun MainCritParams.minimizeScoreDifference(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        val scoreRange: Int = groupsCount
        // TODO check category equality if category are used in SwissCat
        val x = abs(p1.group - p2.group).toDouble() / scoreRange.toDouble()
        val k: Double = pairing.base.nx1
        score = scoreWeight * (1.0 - x) * (1.0 + k * x)

        return score
    }

    fun MainCritParams.applySeeding(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // Apply seeding for players in the same group
        if (p1.group == p2.group) {
            val (cla1, groupSize) = p1.placeInGroup
            val cla2 = p2.placeInGroup.first
            val maxSeedingWeight = seedingWeight

            val currentSeedSystem= if (round <= lastRoundForSeedSystem1) seedSystem1 else seedSystem2

            score += when(currentSeedSystem) {
                // The best is to get 2 * |Cla1 - Cla2| - groupSize close to 0
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

    open fun SecondaryCritParams.apply(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // See Swiss with category for minimizing handicap criterion

        // TODO understand where opengotha test if need to be applied

        return score
    }

    open fun SecondaryCritParams.notNeeded(p1: Pairable, p2: Pairable) {
        // secCase = 0 : No player is above thresholds
        // secCase = 1 : One player is above thresholds
        // secCase = 2 : Both players are above thresholds
        // TODO understand where it is used
    }

    fun GeographicalParams.apply(p1: Pairable, p2: Pairable): Double {
        val placementScoreRange = groupsCount

        val geoMaxCost = avoidSameGeo

        val countryFactor = preferMMSDiffRatherThanSameCountry
        val clubFactor: Int = preferMMSDiffRatherThanSameClub
        //val groupFactor: Int = preferMMSDiffRatherThanSameClubsGroup

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
            geoRatio += 0.5 / placementScoreRange.toDouble()
        }

        // The concavity function is applied to geoRatio to get geoCost
        val dbGeoCost: Double = geoMaxCost.toDouble() * (1.0 - geoRatio) * (1.0 + pairing.base.nx1 * geoRatio)
        var score = pairing.main.scoreWeight - dbGeoCost
        score = min(score, geoMaxCost)

        return score
    }

    // Handicap functions
    // Has to be overridden if handicap is not based on rank
    open fun HandicapParams.handicap(p1: Pairable, p2: Pairable): Int {
        var hd = 0
        var pseudoRank1: Int = p1.rank
        var pseudoRank2: Int = p2.rank

        pseudoRank1 = min(pseudoRank1, rankThreshold)
        pseudoRank2 = min(pseudoRank2, rankThreshold)
        hd = pseudoRank1 - pseudoRank2

        return clamp(hd)
    }

    open fun HandicapParams.clamp(input: Int): Int {
        var hd = input
        if (hd >= correction) hd -= correction
        if (hd < 0) hd = max(hd + correction, 0)
        // Clamp handicap with ceiling
        hd = min(hd, ceiling)
        hd = max(hd, -ceiling)

        return hd
    }

    open fun games(black: Pairable, white: Pairable): List<Game> {
        // CB TODO team of individuals pairing
        return listOf(Game(id = Store.nextGameId, black = black.id, white = white.id, handicap = pairing.handicap.handicap(black, white)))
    }

    // Generic parameters calculation
    //private val standingScore by lazy { computeStandingScore() }

    // Decide each pairable group based on the main criterion
    private val groupsCount get() = (mainLimits.second - mainLimits.first).toInt()
    private val _groups by lazy {
        pairables.associate { pairable -> Pair(pairable.id, pairable.main.toInt()) }
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

    val Pairable.sos: Double get() = historyHelper.sos[id] ?: 0.0
    val Pairable.sosm1: Double get() = historyHelper.sosm1[id] ?: 0.0
    val Pairable.sosm2: Double get() = historyHelper.sosm2[id] ?: 0.0
    val Pairable.sosos: Double get() = historyHelper.sosos[id] ?: 0.0
    val Pairable.sodos: Double get() = historyHelper.sodos[id] ?: 0.0
    val Pairable.cums: Double get() = historyHelper.cumScore[id] ?: 0.0

    fun Pairable.eval(criterion: Criterion) = evalCriterion(this, criterion)
    open fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        NONE -> 0.0
        CATEGORY -> TODO()
        RANK -> pairable.rank.toDouble()
        RATING -> pairable.rating.toDouble()
        NBW -> pairable.nbW
        SOSW -> pairable.sos
        SOSWM1 -> pairable.sosm1
        SOSWM2 -> pairable.sosm2
        SOSOSW -> pairable.sosos
        SODOSW -> pairable.sodos
        CUSSW -> pairable.cums
        else -> throw Error("criterion cannot be evaluated: ${criterion.name}")
    }
}
