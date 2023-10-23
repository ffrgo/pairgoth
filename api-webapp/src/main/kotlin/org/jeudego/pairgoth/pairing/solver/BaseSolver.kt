package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.MainCritParams.SeedMethod.*
import org.jeudego.pairgoth.pairing.BasePairingHelper
import org.jeudego.pairgoth.pairing.detRandom
import org.jeudego.pairgoth.pairing.nonDetRandom
import org.jeudego.pairgoth.store.Store
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.builder.GraphBuilder
import java.io.File
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class BaseSolver(
    val round: Int, // Round number
    history: List<List<Game>>, // History of all games played for each round
    pairables: List<Pairable>, // All pairables for this round, it may include the bye player
    pairing: PairingParams,
    placement: PlacementParams,
    val forcedBye: Pairable? = null, // This parameter is non-null to force the given pairable to be chosen as a bye player.
    ) : BasePairingHelper(history, pairables, pairing, placement) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
        val DEBUG_EXPORT_WEIGHT = true
    }

    open fun openGothaWeight(p1: Pairable, p2: Pairable) =
        1.0 + // 1 is minimum value because 0 means "no matching allowed"
                pairing.base.apply(p1, p2) +
                pairing.main.apply(p1, p2) +
                pairing.secondary.apply(p1, p2) +
                pairing.geo.apply(p1, p2)

    open fun weight(p1: Pairable, p2: Pairable) =
        openGothaWeight(p1, p2) +
        pairing.handicap.color(p1, p2)

    fun pair(): List<Game> {
        // The byeGame is a list of one game with the bye player or an empty list
        val byeGame: List<Game> = if (pairables.size % 2 != 0) {
            // We must choose a bye player
            val physicalByePlayer = forcedBye ?: chooseByePlayer()
            // Remove the bye from the pairables
            pairables = pairables.filterNot { it == physicalByePlayer }
            // Assign a special game to the bye player
            listOf( Game(Store.nextGameId, physicalByePlayer.id, ByePlayer.id) )
        } else {
            listOf()
        }

        return listOf(pairEvenNumberOfPlayers(), byeGame).flatten() // Add the bye game to the actual paired games
    }
    fun pairEvenNumberOfPlayers(): List<Game> {
        // check that at this stage, we have an even number of pairables
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleDirectedWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))

        val WEIGHTS_FILE = "src/test/resources/weights.txt"
        val dec = DecimalFormat("#.#")

        if (DEBUG_EXPORT_WEIGHT){
            File(WEIGHTS_FILE).writeText("Round "+round.toString()+"\n")
            //else File(WEIGHTS_FILE).appendText("Round "+round.toString()+"\n")
            File(WEIGHTS_FILE).appendText("Costs\n")
            // println("placement criteria" + placement.criteria.toString())
        }

        for (i in nameSortedPairables.indices) {
            // println(nameSortedPairables[i].nameSeed() + " id="+nameSortedPairables[i].id.toString()+" clasmt="+nameSortedPairables[i].placeInGroup.toString())
            for (j in i + 1 until pairables.size) {
                val p = nameSortedPairables[i]
                val q = nameSortedPairables[j]
                weight(p, q).let { if (it != Double.NaN) builder.addEdge(p, q, it) }
                weight(q, p).let { if (it != Double.NaN) builder.addEdge(q, p, it) }
                if (DEBUG_EXPORT_WEIGHT)
                {
                    File(WEIGHTS_FILE).appendText("Player1Name="+p.nameSeed()+"\n")
                    File(WEIGHTS_FILE).appendText("Player2Name="+q.nameSeed()+"\n")
                    File(WEIGHTS_FILE).appendText("baseDuplicateGameCost="+dec.format(pairing.base.avoidDuplicatingGames(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("baseRandomCost="+dec.format(pairing.base.applyRandom(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("baseBWBalanceCost="+dec.format(pairing.base.applyColorBalance(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("mainCategoryCost="+dec.format(pairing.main.avoidMixingCategory(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("mainScoreDiffCost="+dec.format(pairing.main.minimizeScoreDifference(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("mainDUDDCost="+dec.format(pairing.main.applyDUDD(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("mainSeedCost="+dec.format(pairing.main.applySeeding(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("secHandiCost="+dec.format(pairing.handicap.handicap(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("secGeoCost="+dec.format(pairing.geo.apply(p, q))+"\n")
                    File(WEIGHTS_FILE).appendText("totalCost="+dec.format(openGothaWeight(p,q))+"\n")
                }
            }
        }
        val graph = builder.build()
        val matching = KolmogorovWeightedPerfectMatching(graph, ObjectiveSense.MAXIMIZE)
        val solution = matching.matching

        val sorted = solution.map{
            listOf(graph.getEdgeSource(it), graph.getEdgeTarget(it))
        }.sortedWith(compareBy({ min(it[0].place, it[1].place) }))

        val result = sorted.flatMap { games(white = it[0], black = it[1]) }

        if (DEBUG_EXPORT_WEIGHT) {
            for (it in sorted) {
                println(it[0].nameSeed() + " " + it[0].place.toString() + " vs " +it[1].nameSeed() + " " +it[1].place.toString())
            }
        }

        return result

    }

    fun chooseByePlayer(): Pairable {
        // TODO https://github.com/lucvannier/opengotha/blob/master/src/info/vannier/gotha/Tournament.java#L1471
        return ByePlayer
    }

    // Base criteria
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
        return score
    }

    open fun BaseCritParams.applyRandom(p1: Pairable, p2: Pairable): Double {
        val score =  if (deterministic) detRandom(random, p1, p2)
        else nonDetRandom(random)
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

        return score
    }

    open fun MainCritParams.applyDUDD(p1: Pairable, p2: Pairable): Double {
        var score = 0.0

        // TODO apply Drawn-Up/Drawn-Down if needed
        // Main Criterion 3 : If different groups, make a directed Draw-up/Draw-down
        // Modifs V3.44.05 (ideas from Tuomo Salo)

        // Main Criterion 3 : If different groups, make a directed Draw-up/Draw-down
        // Modifs V3.44.05 (ideas from Tuomo Salo)
        var duddCost: Long = 0
        if (Math.abs(p1.group - p2.group) < 4 && (p1.group != p2.group)) {
            // 5 scenarii
            // scenario = 0 : Both players have already been drawn in the same sense
            // scenario = 1 : One of the players has already been drawn in the same sense           
            // scenario = 2 : Normal conditions (does not correct anything and no previous drawn in the same sense)
            //                This case also occurs if one DU/DD is increased, while one is compensated
            // scenario = 3 : It corrects a previous DU/DD            //        
            // scenario = 4 : it corrects a previous DU/DD for both
            var scenario = 2
            val p1_DU = 0 // TODO compute DUDD for both players
            val p1_DD = 0
            val p2_DU = 0
            val p2_DD = 0
            
            if (p1_DU > 0 && p1.group > p2.group) {
                scenario--
            }
            if (p1_DD > 0 && p1.group < p2.group) {
                scenario--
            }
            if (p2_DU > 0 && p2.group > p1.group) {
                scenario--
            }
            if (p2_DD > 0 && p2.group < p1.group) {
                scenario--
            }
            if (scenario != 0 && p1_DU > 0 && p1_DD < p1_DU && p1.group < p2.group) {
                scenario++
            }
            if (scenario != 0 && p1_DD > 0 && p1_DU < p1_DD && p1.group > p2.group) {
                scenario++
            }
            if (scenario != 0 && p2_DU > 0 && p2_DD < p2_DU && p2.group < p1.group) {
                scenario++
            }
            if (scenario != 0 && p2_DD > 0 && p2_DU < p2_DD && p2.group > p1.group) {
                scenario++
            }
            val duddWeight: Double = pairing.main.drawUpDownWeight/5.0
            val upperSP = if (p1.group < p2.group) p1 else p2
            val lowerSP = if (p1.group < p2.group) p2 else p1
            val uSPgroupSize = upperSP.placeInGroup.second
            val lSPgroupSize = lowerSP.placeInGroup.second

            if (pairing.main.drawUpDownUpperMode === MainCritParams.DrawUpDown.TOP) {
                score += duddWeight / 2 * (uSPgroupSize - 1 - upperSP.placeInGroup.first) / uSPgroupSize
            } else if (pairing.main.drawUpDownUpperMode === MainCritParams.DrawUpDown.MIDDLE) {
                score += duddWeight / 2 * (uSPgroupSize - 1 - Math.abs(2 * upperSP.placeInGroup.first - uSPgroupSize + 1)) / uSPgroupSize
            } else if (pairing.main.drawUpDownUpperMode === MainCritParams.DrawUpDown.BOTTOM) {
                score += duddWeight / 2 * upperSP.placeInGroup.first / uSPgroupSize
            }
            if (pairing.main.drawUpDownLowerMode === MainCritParams.DrawUpDown.TOP) {
                score += duddWeight / 2 * (lSPgroupSize - 1 - lowerSP.placeInGroup.first) / lSPgroupSize
            } else if (pairing.main.drawUpDownLowerMode === MainCritParams.DrawUpDown.MIDDLE) {
                score += duddWeight / 2 * (lSPgroupSize - 1 - Math.abs(2 * lowerSP.placeInGroup.first - lSPgroupSize + 1)) / lSPgroupSize
            } else if (pairing.main.drawUpDownLowerMode === MainCritParams.DrawUpDown.BOTTOM) {
                score += duddWeight / 2 * lowerSP.placeInGroup.first / lSPgroupSize
            }
            if (scenario == 0) {
                // Do nothing
            } else if (scenario == 1) {
                score += 1 * duddWeight
            } else if (scenario == 2 || scenario > 2 && !pairing.main.compensateDrawUpDown) {
                score += 2 * duddWeight
            } else if (scenario == 3) {
                score += 3 * duddWeight
            } else if (scenario == 4) {
                score += 4 * duddWeight
            }
        }

        // TODO adapt to Swiss with categories
        /*// But, if players come from different categories, decrease score(added in 3.11)
        val catGap: Int = Math.abs(p1.category(gps) - p2.category(gps))
        score = score / (catGap + 1) / (catGap + 1) / (catGap + 1) / (catGap + 1)*/

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
        return Math.round(score).toDouble()
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

    open fun HandicapParams.color(p1: Pairable, p2: Pairable): Double {
        var score = 0.0
        val hd = pairing.handicap.handicap(p1,p2)
        if(hd==0){
            if (p1.colorBalance > p2.colorBalance) {
                score = 1.0
            } else if (p1.colorBalance < p2.colorBalance) {
                score = -1.0
            } else { // choose color from a det random
                if (detRandom(1.0, p1, p2) === 0.0) {
                    score = 1.0
                } else {
                    score = -1.0
                }
            }
        }
        return score
    }

    open fun games(black: Pairable, white: Pairable): List<Game> {
        // CB TODO team of individuals pairing
        return listOf(Game(id = Store.nextGameId, black = black.id, white = white.id, handicap = pairing.handicap.handicap(black, white)))
    }
}
