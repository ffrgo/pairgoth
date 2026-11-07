package org.jeudego.pairgoth.test

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * The pairing cost breakdowns OpenGotha and pairgoth both write: one block per candidate pair, one
 * line per criterion. OpenGotha's come from the instrumented 3.52 build (see the `instrumented-3.52`
 * branch of the opengotha repository), pairgoth's from [org.jeudego.pairgoth.pairing.solver.LoggingListener].
 *
 * A dump may also carry, after a `Pairings` line, the pairing OpenGotha produced for each round —
 * that is what the corpus tests compare against, the costs telling them *why* when it differs.
 */
object OpenGothaWeights {

    val CRITERIA = listOf(
        "baseDuplicateGameCost", "baseRandomCost", "baseBWBalanceCost",
        "mainCategoryCost", "mainScoreDiffCost", "mainDUDDCost", "mainSeedCost",
        "secHandiCost", "secGeoCost"
    )

    /** One player pair, named as both engines name them: "name firstname". */
    data class Pair(val one: String, val two: String) {
        constructor(pair: Set<String>) : this(pair.first(), pair.last())
        private val ordered = setOf(one, two)
        override fun equals(other: Any?) = other is Pair && other.ordered == ordered
        override fun hashCode() = ordered.hashCode()
        override fun toString() = ordered.joinToString(" / ")
    }

    /** The pairing of one round: the games, plus the bye player if there was one. */
    class Pairing(val pairs: Set<Set<String>>, val bye: String?)

    class Dump(val costs: Map<Int, Map<Pair, Map<String, Double>>>, val pairings: Map<Int, Pairing>) {
        fun cost(round: Int, pair: Pair) = costs[round]?.get(pair)
        /** Total cost of a set of pairs under this dump's weights, byes (which have none) apart. */
        fun total(round: Int, pairs: Collection<Set<String>>) =
            pairs.sumOf { costs[round]?.get(Pair(it))?.get("totalCost") ?: 0.0 }
    }

    fun read(file: File): Dump =
        (if (file.name.endsWith(".gz")) GZIPInputStream(file.inputStream()) else file.inputStream()).use { read(it) }

    fun read(input: InputStream): Dump {
        val costs = mutableMapOf<Int, MutableMap<Pair, MutableMap<String, Double>>>()
        val pairs = mutableMapOf<Int, MutableSet<Set<String>>>()
        val byes = mutableMapOf<Int, String>()
        var round = 0
        var one: String? = null
        var current: MutableMap<String, Double>? = null
        input.bufferedReader().forEachLine { line ->
            val (key, value) = line.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
            when {
                line.startsWith("Round ") -> round = line.removePrefix("Round ").trim().toInt()
                key == "Player1Name" -> one = value
                key == "Player2Name" -> current = mutableMapOf<String, Double>().also {
                    costs.getOrPut(round) { mutableMapOf() }[Pair(one!!, value!!)] = it
                }
                key == "Bye" -> {
                    byes[round] = value!!
                    pairs.getOrPut(round) { mutableSetOf() }
                }
                key == "Game" -> value!!.split('|').let { (white, black, _) ->
                    pairs.getOrPut(round) { mutableSetOf() }.add(setOf(white, black))
                }
                value != null && current != null -> current!![key] = value.toDouble()
            }
        }
        return Dump(costs, pairs.keys.associateWith { Pairing(pairs[it]!!, byes[it]) })
    }

    /** How two dumps compare over the candidate pairs they share, criterion by criterion. */
    class Comparison(val compared: Int, val differing: Int, val worst: Map<String, Double>) {
        override fun toString() =
            if (differing == 0) "$compared pairs, identical"
            else "$differing of $compared pairs differ (" +
                worst.entries.sortedByDescending { it.value }.joinToString(", ") { (criterion, delta) ->
                    "$criterion up to %.0f".format(delta)
                } + ")"
    }

    fun compare(round: Int, mine: Dump, theirs: Dump): Comparison {
        val shared = (mine.costs[round]?.keys ?: emptySet<Pair>()).intersect(theirs.costs[round]?.keys ?: emptySet())
        val worst = mutableMapOf<String, Double>()
        var differing = 0
        shared.forEach { pair ->
            val differences = differences(mine.cost(round, pair), theirs.cost(round, pair))
            if (differences.isNotEmpty()) differing++
            differences.forEach { (criterion, delta) ->
                worst[criterion] = maxOf(worst[criterion] ?: 0.0, abs(delta))
            }
        }
        return Comparison(shared.size, differing, worst)
    }

    /**
     * Component-by-component comparison of the same pairs in two dumps, silent on what matches.
     * pairgoth's listener reports secHandi as 0 and leaves its own colour-balance criterion out, so
     * only the criteria OpenGotha also computes are compared.
     */
    fun differences(mine: Map<String, Double>?, theirs: Map<String, Double>?): Map<String, Double> {
        if (mine == null || theirs == null) return emptyMap()
        return CRITERIA.associateWith { (mine[it] ?: 0.0) - (theirs[it] ?: 0.0) }.filterValues { abs(it) > 0.5 }
    }

    // --- the older per-round reference files, kept in the format the conformance tests use ---

    fun create_weights_map(file: File): HashMap<kotlin.Pair<String, String>, List<Double>> {
        val map = HashMap<kotlin.Pair<String, String>, List<Double>>()
        val dump = read(file)
        for ((_, roundCosts) in dump.costs) {
            for ((pair, costs) in roundCosts) {
                val key =
                    if (pair.one > pair.two) kotlin.Pair(pair.one, pair.two)
                    else kotlin.Pair(pair.two, pair.one)
                map[key] = (CRITERIA + "totalCost").map { costs[it] ?: 0.0 }
            }
        }
        return map
    }

    fun compare_weights(file1: File, file2: File, skipSeeding: Boolean = false): Boolean {
        val map1 = create_weights_map(file1)
        val map2 = create_weights_map(file2)
        var identical = true
        for ((key, value) in map1) {
            val other = map2[key] ?: continue
            val differs =
                if (!skipSeeding) abs(value[9] - other[9]) > 10 && identical
                else abs((value[9] - value[6] - value[5]) - (other[9] - other[6] - other[5])) > 10 && identical
            if (differs) {
                TestBase.logger.info("Difference found at $key")
                TestBase.logger.info("                          pairgoth    opengotha")
                (CRITERIA + "totalCost").forEachIndexed { i, criterion ->
                    TestBase.logger.info("${criterion.padEnd(22)} =   ${value[i]}   ${other[i]}")
                }
                identical = false
            }
        }
        return identical
    }
}
