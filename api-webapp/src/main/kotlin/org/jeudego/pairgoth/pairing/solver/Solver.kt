package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.MainCritParams.DrawUpDown
import org.jeudego.pairgoth.model.MainCritParams.SeedMethod.*
import org.jeudego.pairgoth.pairing.BasePairingHelper
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.detRandom
import org.jeudego.pairgoth.pairing.nonDetRandom
import org.jeudego.pairgoth.store.nextGameId
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.builder.GraphBuilder
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.text.DecimalFormat
import java.util.*
import kotlin.math.*

sealed class Solver(
    round: Int,
    totalRounds: Int,
    history: HistoryHelper,    // Digested history of all games played for each round
    pairables: List<Pairable>, // Pairables to pair together
    val allPairablesMap: Map<ID, Pairable>, // Map of all known pairables
    pairing: PairingParams,
    placement: PlacementParams,
    val usedTables: BitSet
    ) : BasePairingHelper(round, totalRounds, history, pairables, pairing, placement) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    // For tests and explain feature
    var legacyMode = false
    var pairingListener: PairingListener? = null

    init {
        history.scoresFactory = this::mainScoreMapFactory
        history.scoresXFactory = this::scoreXMapFactory
        history.missedRoundsSosFactory = this::missedRoundSosMapFactory
    }

    /**
     * Main score map factory (NBW for Swiss, MMS for MacMahon, ...).
     */
    abstract fun mainScoreMapFactory(): Map<ID, Double>

    /**
     * ScoreX map factory (NBW for Swiss, MMSBase + MMS for MacMahon, ...).
     */
    abstract fun scoreXMapFactory(): Map<ID, Double>

    /**
     * SOS for missed rounds factory (0 for Swiss, mmBase or mmBase+rounds/2 for MacMahon depending on pairing option sosValueAbsentUseBase)
     */
    abstract fun missedRoundSosMapFactory(): Map<ID, Double>

    open fun openGothaWeight(p1: Pairable, p2: Pairable) =
        1.0 + // 1 is the minimum value because 0 means "no matching allowed"
                pairing.base.apply(p1, p2) +
                pairing.main.apply(p1, p2) +
                pairing.secondary.apply(p1, p2)

    open fun pairgothBlackWhite(p1: Pairable, p2: Pairable): Double {
        val hd1 = pairing.handicap.handicap(white = p1, black = p2)
        val hd2 = pairing.handicap.handicap(white = p2, black = p1)
        return if (hd1 > 0 && hd2 == 0) pairing.base.colorBalanceWeight * 10
        else 0.0
    }

    open fun weight(p1: Pairable, p2: Pairable): Double {
        pairingListener?.startPair(p1, p2)
        return (
            openGothaWeight(p1, p2) +
            pairgothBlackWhite(p1, p2).also { pairingListener?.addWeight("secColorBalance", it) } +
            // pairing.base.applyByeWeight(p1, p2) +
            pairing.handicap.color(p1, p2).also { pairingListener?.addWeight("secHandi", it) }
        ).also {
            pairingListener?.endPair(p1, p2)
        }
    }

    open fun computeWeightForBye(p: Pairable): Double {
        // The weightForBye function depends on the system type (Mac-Mahon or Swiss), default value is 0.0
        return 0.0
    }

    fun pair(): List<Game> {
        // check that at this stage, we have an even number of pairables
        // The BYE player should have been added beforehand to make the number of pairables even.
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleDirectedWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))

        val dec = DecimalFormat("#.#")
        val logger = LoggerFactory.getLogger("debug")
        val debug = false

        pairingListener?.start(round)

        var chosenByePlayer: Pairable = ByePlayer
        // Choose bye player and remove from pairables
        if (ByePlayer in nameSortedPairables){
            nameSortedPairables.remove(ByePlayer)
            var minWeight = 1000.0 * round + (Pairable.MAX_RANK - Pairable.MIN_RANK) + 1;
            var weightForBye : Double
            var byePlayerIndex = 0
            for (p in nameSortedPairables){
                weightForBye = computeWeightForBye(p)
                if (p.id in history.byePlayers) weightForBye += 1000
                if (weightForBye <= minWeight){
                    minWeight = weightForBye
                    chosenByePlayer = p
                }
            }
            if(debug) logger.info("Bye player : " + chosenByePlayer.fullName())
            nameSortedPairables.remove(chosenByePlayer)
            // Keep chosenByePlayer in pairingSortedPairables to be identical to opengotha
            pairingSortedPairables.remove(ByePlayer)
        }

        for (i in nameSortedPairables.indices) {
            for (j in i + 1 until nameSortedPairables.size) {
                val p = nameSortedPairables[i]
                val q = nameSortedPairables[j]
                weight(p, q).let { if (it != Double.NaN) builder.addEdge(p, q, it/1e6) }
                weight(q, p).let { if (it != Double.NaN) builder.addEdge(q, p, it/1e6) }
            }
        }
        val graph = builder.build()
        val matching = KolmogorovWeightedPerfectMatching(graph, ObjectiveSense.MAXIMIZE)
        val solution = matching.matching

        val sorted = solution.map{
            listOf(graph.getEdgeSource(it), graph.getEdgeTarget(it))
        }.sortedWith(compareBy { min(it[0].place, it[1].place) })

        var result = sorted.flatMap { games(white = it[0], black = it[1]) }
        // add game for ByePlayer
        if (chosenByePlayer != ByePlayer) result += Game(id = nextGameId, table = 0, white = chosenByePlayer.id, black = ByePlayer.id, result = Game.Result.fromSymbol('w'))

        pairingListener?.end()

        if (debug) {
            var sumOfWeights = 0.0

            logger.info(String.format("%-20s", "Name")
                    + " " + String.format("%-4s", "ID")
                    + " " + String.format("%-4s", "iniS")
                    + " " + String.format("%-4s", "curS")
                    + " " + String.format("%-4s", "SOS")
            )

            for (p in sortedPairables) {
                logger.info(String.format("%-20s", p.name.substring(0, min(p.name.length, 18)))
                        + " " + String.format("%-4s", p.id)
                        + " " + String.format("%-4s", history.missedRoundsSos[p.id])
                        + " " + String.format("%-4s", history.scores[p.id])
                        + " " + String.format("%-4s", p.sos)
                )
            }


            logger.info(String.format("%-20s", "Name")
                    + " " + String.format("%-3s", "plc")
                    + " " + String.format("%-3s", "ID")
                    + " " + String.format("%-3s", "col")
                    + " " + String.format("%-3s", "grp")
                    + " " + String.format("%-10s", "DUDD")
                    + " vs " + String.format("%-20s", "Name")
                    + " " + String.format("%-3s", "plc")
                    + " " + String.format("%-3s", "ID")
                    + " " + String.format("%-3s", "col")
                    + " " + String.format("%-3s", "grp")
                    + " " + String.format("%-10s", "DUDD")
            )

            for (it in sorted) {

                logger.info(String.format("%-20s", it[0].fullName().substring(0, min(it[0].fullName().length, 18)))
                        + " " + String.format("%-3s", it[0].place.toString())
                        + " " + String.format("%-3s", it[0].id.toString())
                        + " " + String.format("%-3s", it[0].colorBalance.toString())
                        + " " + String.format("%-3s", it[0].group.toString())
                        + " " + String.format("%-10s", it[0].drawnUpDown.toString())
                        + " vs " + String.format("%-20s", it[1].fullName().substring(0, min(it[1].fullName().length, 18)))
                        + " " + String.format("%-3s", it[1].place.toString())
                        + " " + String.format("%-3s", it[1].id.toString())
                        + " " + String.format("%-3s", it[1].colorBalance.toString())
                        + " " + String.format("%-3s", it[1].group.toString())
                        + " " + String.format("%-10s", it[1].drawnUpDown.toString())
                )

                sumOfWeights += weight(it[0], it[1])
            }
            val dec = DecimalFormat("#.#")
            logger.info("sumOfWeights = " + dec.format(sumOfWeights))
        }
        return result
    }

    // Base criteria
    open fun BaseCritParams.apply(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        // Base Criterion 1 : Avoid Duplicating Game
        // Did p1 and p2 already play ?
        score += avoidDuplicatingGames(p1, p2).also { pairingListener?.addWeight("baseDuplicateGame", it) }
        // Base Criterion 2 : Random
        score += applyRandom(p1, p2).also { pairingListener?.addWeight("baseRandom", it) }
        // Base Criterion 3 : Balance W and B
        score += applyColorBalance(p1, p2).also { pairingListener?.addWeight("baseColorBalance", it) }

        return score
    }

    // Weight score computation details
    open fun  BaseCritParams.avoidDuplicatingGames(p1: Pairable, p2: Pairable): Double {
        val score =  if (p1.played(p2)) 0.0 // We get no score if pairables already played together
        else dupWeight
        return score
    }

    open fun BaseCritParams.applyRandom(p1: Pairable, p2: Pairable): Double {
        val score =  if (deterministic) detRandom(random, p1, p2, true)
        else nonDetRandom(random)
        return score
    }

    open fun BaseCritParams.applyColorBalance(p1: Pairable, p2: Pairable): Double {
        if (p1.id == 0 || p2.id == 0) return 0.0
        // This cost is never applied if potential Handicap != 0
        // It is fully applied if wbBalance(sP1) and wbBalance(sP2) are strictly of different signs
        // It is half applied if one of wbBalance is 0 and the other is >=2
        val hd1 = pairing.handicap.handicap(white = p1, black = p2)
        val hd2 = pairing.handicap.handicap(white = p2, black = p1)
        val potentialHd: Int = max(hd1, hd2)

        val score = if (potentialHd == 0) {
            val wb1: Int = p1.colorBalance
            val wb2: Int = p2.colorBalance
            if (wb1 * wb2 < 0) colorBalanceWeight
            else if (wb1 == 0 && abs(wb2) >= 2 || wb2 == 0 && abs(wb1) >= 2) colorBalanceWeight / 2 else 0.0
        } else {
            /* Moved to pairgothBlackWhite()
            // apply a *big* score to let the stronger player have white
            if (hd1 > 0 && hd2 == 0 && !avoidPairgothSpecificWeights) colorBalanceWeight * 10
            else */ 0.0
        }
        return score
    }

    open fun BaseCritParams.applyByeWeight(p1: Pairable, p2: Pairable): Double {
        // The weight is applied if one of p1 or p2 is the BYE player
        return if (p1.id == ByePlayer.id || p2.id == ByePlayer.id) {
            val actualPlayer = if (p1.id == ByePlayer.id) p2 else p1
            // TODO maybe use a different formula than opengotha
            val x = (actualPlayer.rank - Pairable.MIN_RANK + actualPlayer.main) / (Pairable.MAX_RANK - Pairable.MIN_RANK + mainLimits.second)
            //concavityFunction(x, BaseCritParams.MAX_BYE_WEIGHT)
            //BaseCritParams.MAX_BYE_WEIGHT - (actualPlayer.rank + 2*actualPlayer.main)
            BaseCritParams.MAX_BYE_WEIGHT*(1 - x)
        } else {
            0.0
        }
    }

    // Main criteria
    open fun MainCritParams.apply(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // Main criterion 1 avoid mixing category is moved to Swiss with category
        score += avoidMixingCategory(p1, p2).also { pairingListener?.addWeight("mainCategory", it) }

        // Main criterion 2 minimize score difference
        score += minimizeScoreDifference(p1, p2).also { pairingListener?.addWeight("mainScoreDiff", it) }

        // Main criterion 3 If different groups, make a directed Draw-up/Draw-down
        score += applyDUDD(p1, p2).also { pairingListener?.addWeight("mainDUDD", it) }

        // Main criterion 4 seeding
        score += applySeeding(p1, p2).also { pairingListener?.addWeight("mainSeed", it) }

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
        if (scoreRange != 0){
            val x = abs(p1.group - p2.group).toDouble() / scoreRange.toDouble()
            score = concavityFunction(x, scoreWeight)
        }

        return score
    }

    // helper function for DUDD
    fun duddForGroup(duddMode: DrawUpDown, p: Pairable, duddWeight: Double): Double {
        val (placeInGroup, groupSize) = p.placeInGroup
        return when (duddMode) {
            DrawUpDown.TOP -> duddWeight / 2 * (groupSize - 1 - placeInGroup) / groupSize
            DrawUpDown.MIDDLE -> duddWeight / 2 * (groupSize - 1 - abs(2 * placeInGroup - groupSize + 1)) / groupSize
            DrawUpDown.BOTTOM -> duddWeight / 2 * placeInGroup / groupSize
        }
     }

    open fun MainCritParams.applyDUDD(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // Main Criterion 3 : If different groups, make a directed Draw-up/Draw-down
        // Modifs Opengotha V3.44.05 (ideas from Tuomo Salo)

        if (p1.group != p2.group) {
            val upperSP = if (p1.group < p2.group) p2 else p1
            val lowerSP = if (p1.group < p2.group) p1 else p2
            // 5 scenarii
            // scenario = 0 : Both players have already been drawn in the same sense
            // scenario = 1 : One of the players has already been drawn in the same sense           
            // scenario = 2 : Normal conditions (does not correct anything and no previous drawn in the same sense)
            //                This case also occurs if one DU/DD is increased, while one is compensated
            // scenario = 3 : It corrects a previous DU/DD            //        
            // scenario = 4 : it corrects a previous DU/DD for both
            var scenario = 2
            val (uSP_DU, uSP_DD) = upperSP.drawnUpDown
            val (lSP_DU, lSP_DD) = lowerSP.drawnUpDown
            
			// If upper player has already been drawn down or lower player has already been drawn up
			// This is bad
            if (uSP_DD > 0) {
                scenario--
            }
            if (lSP_DU > 0) {
                scenario--
            }
            // if top player has already been drawn down more than drawn up
            // This is good
            if (scenario != 0 && uSP_DU > 0 && uSP_DU > uSP_DD) {
                scenario++
            }
            // Conversely if lower player has been drawn up more than drawn down
            if (scenario != 0 && lSP_DD > 0 && lSP_DD > lSP_DU) {
                scenario++
            }

            val duddWeight = pairing.main.drawUpDownWeight / 5.0

            score +=
                duddForGroup(pairing.main.drawUpDownUpperMode, upperSP, duddWeight) +
                duddForGroup(pairing.main.drawUpDownLowerMode, lowerSP, duddWeight)

            if (scenario == 0) {
                // Do nothing
            } else if (scenario == 1) {
                score += 1 * duddWeight
            } else if (scenario == 2 || (scenario > 2 && !pairing.main.compensateDrawUpDown)) {
                score += 2 * duddWeight
            } else if (scenario == 3) {
                score += 3 * duddWeight
            } else if (scenario == 4) {
                score += 4 * duddWeight
            }

        }
        score = max(0.0, score)
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
                        // for old tests to pass
                        val rand =
                            if (legacyMode && p1.fullName() > p2.fullName()) {
                                // for old tests to pass
                                detRandom(randRange, p2, p1, false)
                            } else {
                                detRandom(randRange, p1, p2, true)
                            }
                        maxSeedingWeight - rand
                    } else {
                        0.0
                    }
                }
            }
        }
        return Math.round(score).toDouble()
    }

    open fun SecondaryCritParams.playersMeetCriteria(p1: Pairable, p2: Pairable): Int {
        // playersMeetCriteria = 0 : No player is above thresholds -> apply secondary criteria
        // playersMeetCriteria = 1 : 1 player is above thresholds -> apply half the weight
        // playersMeetCriteria = 2 : Both players are above thresholds -> apply the full weight

        var playersMeetCriteria = 0
        val nbw2Threshold =
            if (nbWinsThresholdActive) totalRounds
            else 2 * totalRounds

        if (2*p1.nbW >= nbw2Threshold) playersMeetCriteria++
        if (2*p2.nbW >= nbw2Threshold) playersMeetCriteria++

        return playersMeetCriteria
    }

    fun SecondaryCritParams.apply(p1: Pairable, p2: Pairable): Double {
        return pairing.geo.apply(p1, p2, playersMeetCriteria(p1, p2))
    }

    fun GeographicalParams.apply(p1: Pairable, p2: Pairable, playersMeetCriteria: Int): Double {
        val placementScoreRange = groupsCount

        val geoMaxCost = pairing.geo.avoidSameGeo

		val countryFactor: Int = if (legacyMode || biggestCountrySize.toDouble() / pairables.size <= proportionMainClubThreshold)
			preferMMSDiffRatherThanSameCountry
		else
			0
		val clubFactor: Int = if (legacyMode || biggestClubSize.toDouble() / pairables.size <= proportionMainClubThreshold)
			preferMMSDiffRatherThanSameClub
		else
			0
        //val groupFactor: Int = preferMMSDiffRatherThanSameClubsGroup

        // Same country
        val countryRatio = if (p1.country != p2.country && countryFactor != 0) {
            min(countryFactor.toDouble() / placementScoreRange.toDouble(), 1.0) // clamp to 1
        } else {
            0.0
        }

        // Same club and club group (TODO club group)
        var clubRatio = 0.0
        // To match OpenGotha, only do a case insensitive comparison of the first four characters.
        // But obviously, there is a margin of improvement here towards some way of normalizing clubs.
        val commonClub = p1.club?.take(4)?.uppercase() == p2.club?.take(4)?.uppercase()
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
        val dbGeoCost: Double = concavityFunction(geoRatio, geoMaxCost)
        var geoNominalCost = pairing.main.scoreWeight - dbGeoCost

        if (geoNominalCost > geoMaxCost) geoNominalCost = geoMaxCost

        return when (playersMeetCriteria) {
            2 -> geoMaxCost
            1 -> 0.5 * (geoNominalCost + geoMaxCost)
            else -> geoNominalCost
        }.also { pairingListener?.addWeight("secGeo", it) }
    }

    // Handicap functions
    fun HandicapParams.handicap(white: Pairable, black: Pairable): Int {
        var pseudoRankWhite: Int = pseudoRank(white)
        var pseudoRankBlack: Int = pseudoRank(black)

        pseudoRankWhite = min(pseudoRankWhite, rankThreshold)
        pseudoRankBlack = min(pseudoRankBlack, rankThreshold)
        return clamp(pseudoRankWhite - pseudoRankBlack)
    }

    // Has to be overridden if handicap is not based on rank
    open fun HandicapParams.pseudoRank(pairable: Pairable): Int {
        return pairable.rank
    }

    fun roundScore(score: Double): Double {
        val epsilon = 0.00001
        // Note: this works for now because we only have .0 and .5 fractional parts
        return if (pairing.main.roundDownScore) floor(score + epsilon)
        else round(2 * score) / 2
    }

    open fun HandicapParams.clamp(input: Int): Int {
        var hd = input
        // TODO - validate that "correction" is >= 0 (or modify the UI and the following code to handle the <0 case)
        if (hd >= correction) hd -= correction
        // TODO - Following line seems buggy... Get rid of it! What as the purpose?!
        // else if (hd < 0) hd = max(hd + correction, 0)
        else hd = 0
        // Clamp handicap with ceiling
        hd = min(hd, ceiling)
        hd = max(hd, -ceiling)

        return hd
    }

    open fun HandicapParams.color(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        val hd = pairing.handicap.handicap(white = p1, black = p2)
        if (hd == 0) {
            if (p1.colorBalance > p2.colorBalance) {
                score = -1.0
            } else if (p1.colorBalance < p2.colorBalance) {
                score = 1.0
            } else { // choose color from a det random
                if (detRandom(1.0, p1, p2, false) == 0.0) {
                    score = 1.0
                } else {
                    score = -1.0
                }
            }
        }
        return score
    }

    fun concavityFunction(x: Double, scale: Double) : Double {
        val k = pairing.base.nx1
        return scale * (1.0 - x) * (1.0 + k * x)
    }

    fun dudd(black: Pairable, white: Pairable): Int {
        // bigger main means stronger group
        // the dudd is given from white perspective
        return if (white.main > black.main) {
            1
        } else if (white.main < black.main) {
            -1
        } else {
            0
        }
    }
    fun hd(white: Pairable, black: Pairable): Int {
        return pairing.handicap.handicap(white = white, black = black)
    }
    open fun games(white: Pairable, black: Pairable): List<Game> {
        // CB TODO team of individuals pairing
        val table = if (black.id == 0 || white.id == 0) 0 else usedTables.nextClearBit(1)
        usedTables.set(table)
        return listOf(Game(id = nextGameId, table = table, black = black.id, white = white.id, handicap = hd(white = white, black = black), drawnUpDown = dudd(black, white)))
    }
}
