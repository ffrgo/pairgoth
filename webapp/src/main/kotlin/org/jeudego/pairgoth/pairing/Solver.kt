package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.*
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.store.Store
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import org.jgrapht.graph.builder.GraphBuilder
import java.util.*

sealed class Solver(protected val history: List<Game>, protected val pairables: List<Pairable>) {

    companion object {
        val rand = Random(/* seed from properties - TODO */)
    }

    open fun sort(p: Pairable, q: Pairable): Int = 0 // no sort by default
    abstract fun weight(p: Pairable, q: Pairable): Double

    fun pair(): List<Game> {
        // check that at this stage, we have an even number of pairables
        if (pairables.size % 2 != 0) throw Error("expecting an even number of pairables")
        val builder = GraphBuilder(SimpleWeightedGraph<Pairable, DefaultWeightedEdge>(DefaultWeightedEdge::class.java))
        for (i in sortedPairables.indices) {
            for (j in i + 1 until n) {
                val p = pairables[i]
                val q = pairables[j]
                builder.addEdge(p, q, weight(p, q))
            }
        }
        val graph = builder.build()
        val matching = KolmogorovWeightedPerfectMatching(graph, ObjectiveSense.MINIMIZE)
        val solution = matching.matching

        val result = solution.map {
            // CB TODO - choice of colors should be here
            Game(Store.nextGameId, graph.getEdgeSource(it).id , graph.getEdgeTarget(it).id)
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

    // score (number of wins)
    val Pairable.score: Int get() = _score[id] ?: 0
    private val _score: Map<Int, Int> by lazy {
        history.mapNotNull { game ->
            when (game.result) {
                BLACK -> game.black
                WHITE -> game.white
                else -> null
            }
        }.groupingBy { it }.eachCount()
    }

    // sos
    val Pairable.sos: Int get() = _sos[id] ?: 0
    private val _sos by lazy {
        (history.map { game ->
            Pair(game.black, _score[game.white] ?: 0)
        } + history.map { game ->
            Pair(game.white, _score[game.black] ?: 0)
        }).groupingBy { it.first }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    // sosos
    val Pairable.sosos: Int get() = _sosos[id] ?: 0
    private val _sosos by lazy {
        (history.map { game ->
            Pair(game.black, _sos[game.white] ?: 0)
        } + history.map { game ->
            Pair(game.white, _sos[game.black] ?: 0)
        }).groupingBy { it.first }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    // sodos
    val Pairable.sodos: Int get() = _sodos[id] ?: 0
    private val _sodos by lazy {
        (history.map { game ->
            Pair(game.black, if (game.result == BLACK) _score[game.white] ?: 0 else 0)
        } + history.map { game ->
            Pair(game.white, if (game.result == WHITE) _score[game.black] ?: 0 else 0)
        }).groupingBy { it.first }.fold(0) { acc, next ->
            acc + next.second
        }
    }

}
