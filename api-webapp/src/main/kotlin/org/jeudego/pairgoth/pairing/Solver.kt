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
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
        val DEBUG_EXPORT_WEIGHT = true
    }

    abstract val scores: Map<ID, Double>
    val historyHelper = if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history) { scores }
        else HistoryHelper(history) { scores }

    // pairables sorted using overloadable sort function
    private val sortedPairables by lazy {
        pairables.sortedWith(::sort)
    }
    // Sorting for logging purpose
    private val logSortedPairablesMap by lazy {
        val logSortedPairables = pairables.sortedWith(::logSort)
        logSortedPairables.associateWith { logSortedPairables.indexOf(it) }
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
    // Sorting function to order the weight matrix for debugging
    open fun logSort(p: Pairable, q: Pairable): Int {
        if (p.rating == q.rating) {
            return if (p.name > q.name) 1 else -1
        }
        return p.rating - q.rating
    }

    open fun weight(p1: Pairable, p2: Pairable) =
        // 1.0 + // 1 is minimum value because 0 means "no matching allowed"
        pairing.base.apply(p1, p2) +
        pairing.main.apply(p1, p2) +
        pairing.secondary.apply(p1, p2) +
        pairing.geo.apply(p1, p2)


    fun weightsToFile(p1: Pairable, p2: Pairable) {
            val pos1: Int = logSortedPairablesMap[p1]!!
            val pos2: Int = logSortedPairablesMap[p2]!!
            //println("Player1Name="+p1.nameSeed())
            //println("Player2Name="+p2.nameSeed())
            //println("total: "+weightLogs["total"]!![pos1][pos2])
            //println("total: "+weightLogs["total"]!![pos1][pos2])
            // println(DEBUG_EXPORT_WEIGHT)
            //println(weightLogs["tot"]!![pos1][pos2])
            //println(p1)
            //println(p2)
            //println(weight)

    }

    // The main criterion that will be used to define the groups should be defined by subclasses
    val Pairable.main: Double get() = scores[id] ?: 0.0
    abstract val mainLimits: Pair<Double, Double>
    // SOS and variants will be computed based on this score
    fun pair(): List<Game> {
        weightLogs.clear()
        // check that at this stage, we have an even number of pairables
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleDirectedWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))
        var WEIGHTS_FILE = "src/test/resources/weights.txt"
        if (DEBUG_EXPORT_WEIGHT){
            if (round==1) File(WEIGHTS_FILE).writeText("Round 1\n")
            else File(WEIGHTS_FILE).appendText("Round "+round.toString()+"\n")
            File(WEIGHTS_FILE).appendText("Costs\n")
        }
        for (i in sortedPairables.indices) {
            for (j in i + 1 until pairables.size) {
                val p = pairables[i]
                val q = pairables[j]
                weight(p, q).let { if (it != Double.NaN) builder.addEdge(p, q, it) }
                weight(q, p).let { if (it != Double.NaN) builder.addEdge(q, p, it) }
                if (DEBUG_EXPORT_WEIGHT)
                {
                    File(WEIGHTS_FILE).appendText("Player1Name="+p.nameSeed()+"\n")
                    File(WEIGHTS_FILE).appendText("Player2Name="+q.nameSeed()+"\n")
                    File(WEIGHTS_FILE).appendText("baseDuplicateGameCost="+pairing.base.avoidDuplicatingGames(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("baseRandomCost="+pairing.base.applyRandom(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("baseBWBalanceCost="+pairing.base.applyColorBalance(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("mainCategoryCost="+pairing.main.avoidMixingCategory(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("mainScoreDiffCost="+pairing.main.minimizeScoreDifference(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("mainDUDDCost="+pairing.main.applyDUDD(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("mainSeedCost="+pairing.main.applySeeding(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("secHandiCost="+pairing.handicap.handicap(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("secGeoCost="+pairing.geo.apply(p, q).toString()+"\n")
                    File(WEIGHTS_FILE).appendText("totalCost="+weight(p,q).toString()+"\n")
                    //%.2f".format(pi)
                    //println(weight(q,p))
                    logWeights("total", p, q, weight(p,q))
                    //weightsToFile(p, q)
                    //println("total weight="+weight(p, q))
                }
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

    var weightLogs: MutableMap<String, Array<DoubleArray>> = mutableMapOf()
    fun logWeights(weightName: String, p1: Pairable, p2: Pairable, weight: Double) {
        if (DEBUG_EXPORT_WEIGHT) {
            if (!weightLogs.contains(weightName)) {
                weightLogs[weightName] = Array(pairables.size) { DoubleArray(pairables.size) }
            }
            val pos1: Int = logSortedPairablesMap[p1]!!
            val pos2: Int = logSortedPairablesMap[p2]!!
            weightLogs[weightName]!![pos1][pos2] = weight

        }
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
        val score =  if (p1.played(p2)) 0.0 // We get no score if pairables already played together
        else dupWeight
        logWeights("avoiddup", p1, p2, score)
        return score
    }

    open fun BaseCritParams.applyRandom(p1: Pairable, p2: Pairable): Double {
        val score =  if (deterministic) detRandom(random, p1, p2)
        else nonDetRandom(random)
        logWeights("random", p1, p2, score)
        return score
    }

    open fun BaseCritParams.applyColorBalance(p1: Pairable, p2: Pairable): Double {
        // This cost is never applied if potential Handicap != 0
        // It is fully applied if wbBalance(sP1) and wbBalance(sP2) are strictly of different signs
        // It is half applied if one of wbBalance is 0 and the other is >=2
        val potentialHd: Int = pairing.handicap.handicap(p1, p2)
        val score = if (potentialHd == 0) {
            val wb1: Int = p1.colorBalance
            val wb2: Int = p2.colorBalance
            if (wb1 * wb2 < 0) colorBalanceWeight
            else if (wb1 == 0 && abs(wb2) >= 2 || wb2 == 0 && abs(wb1) >= 2) colorBalanceWeight / 2 else 0.0
        } else 0.0
        logWeights("color", p1, p2, score)
        return score
    }

    // Main criteria
    open fun MainCritParams.apply(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // Main criterion 1 avoid mixing category is moved to Swiss with category
        score += avoidMixingCategory(p1, p2)

        // Main criterion 2 minimize score difference
        score += minimizeScoreDifference(p1, p2)

        // Main criterion 3 If different groups, make a directed Draw-up/Draw-down
        score += applyDUDD(p1, p2)

        // Main criterion 4 seeding
        score += applySeeding(p1, p2)

        return score
    }

    open fun MainCritParams.avoidMixingCategory(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // TODO check category equality if category are used in SwissCat

        return score
    }

    open fun MainCritParams.minimizeScoreDifference(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        val scoreRange: Int = groupsCount
        // TODO check category equality if category are used in SwissCat
        if (scoreRange!=0){
            val x = abs(p1.group - p2.group).toDouble() / scoreRange.toDouble()
            val k: Double = pairing.base.nx1
            score = scoreWeight * (1.0 - x) * (1.0 + k * x)
        }

        logWeights("score", p1, p2, score)
        return score
    }

    open fun MainCritParams.applyDUDD(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // TODO apply Drawn-Up/Drawn-Down if needed

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
        logWeights("seed", p1, p2, score)
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
            min(countryFactor.toDouble() / placementScoreRange.toDouble(), 1.0) // clamp to 1
        } else {
            0.0
        }
        //println("countryRatio="+countryRatio.toString())

        // Same club and club group (TODO club group)
        var clubRatio = 0.0
        val commonClub = p1.club == p2.club
        val commonGroup = false // TODO

        if (commonGroup && !commonClub) {
            clubRatio = if (clubFactor == 0) {
                0.0
            } else {
                clubFactor.toDouble() / 2.0 / placementScoreRange.toDouble()
            }

        } else if (!commonGroup && !commonClub) {
            clubRatio = if (clubFactor == 0) {
                0.0
            } else {
                clubFactor.toDouble() * 1.2 / placementScoreRange.toDouble()
            }
        }
        clubRatio = min(clubRatio, 1.0)

        // TODO Same family

        // compute geoRatio

        val mainPart = max(countryRatio, clubRatio)
        val secPart = min(countryRatio, clubRatio)

        var geoRatio = mainPart + secPart / 2.0

        if (geoRatio > 0.0 && placementScoreRange != 0) {
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
