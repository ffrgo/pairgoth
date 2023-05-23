package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.*
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.store.Store
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.graph.builder.GraphBuilder
import java.util.*

sealed class Solver(val history: List<Game>, val pairables: List<Pairable>, val weights: Pairing.Weights) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    open fun sort(p: Pairable, q: Pairable): Int = 0 // no sort by default
    abstract fun weight(black: Pairable, white: Pairable): Double
    open fun handicap(black: Pairable, white: Pairable) = 0
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

    // Calculation parameters

    val n = pairables.size

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
    fun Pairable.played(other: Pairable) = _paired.contains(Pair(id, other.id))
    private val _paired: Set<Pair<Int, Int>> by lazy {
        (history.map { game ->
            Pair(game.black, game.white)
        } + history.map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // color balance (nw - nb)
    val Pairable.colorBalance: Int get() = _colorBalance[id] ?: 0
    private val _colorBalance: Map<Int, Int> by lazy {
        history.flatMap { game ->
            listOf(Pair(game.white, +1), Pair(game.black, -1))
        }.groupingBy { it.first }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    // score (number of wins)
    val Pairable.score: Double get() = _score[id] ?: 0.0
    private val _score: Map<Int, Double> by lazy {
        mutableMapOf<Int, Double>().apply {
            history.forEach { game ->
                when (game.result) {
                    BLACK -> put(game.black, getOrDefault(game.black, 0.0) + 1.0)
                    WHITE -> put(game.white, getOrDefault(game.white, 0.0) + 1.0)
                    BOTHWIN -> {
                        put(game.black, getOrDefault(game.black, 0.0) + 0.5)
                        put(game.white, getOrDefault(game.white, 0.0) + 0.5)
                    }
                    else -> {}
                }
            }
        }
    }

    // sos
    val Pairable.sos: Double get() = _sos[id] ?: 0.0
    private val _sos by lazy {
        (history.map { game ->
            Pair(game.black, _score[game.white] ?: 0.0)
        } + history.map { game ->
            Pair(game.white, _score[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sosos
    val Pairable.sosos: Double get() = _sosos[id] ?: 0.0
    private val _sosos by lazy {
        (history.map { game ->
            Pair(game.black, _sos[game.white] ?: 0.0)
        } + history.map { game ->
            Pair(game.white, _sos[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sodos
    val Pairable.sodos: Double get() = _sodos[id] ?: 0.0
    private val _sodos by lazy {
        (history.map { game ->
            Pair(game.black, if (game.result == BLACK) _score[game.white] ?: 0.0 else 0.0)
        } + history.map { game ->
            Pair(game.white, if (game.result == WHITE) _score[game.black] ?: 0.0 else 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

}
