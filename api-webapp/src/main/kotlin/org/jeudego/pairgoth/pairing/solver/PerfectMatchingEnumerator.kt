package org.jeudego.pairgoth.pairing.solver

import org.jgrapht.Graph
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import java.util.PriorityQueue

/**
 * Lazily enumerates the *optimal* (tied weight) perfect matchings of a weighted graph, one at a
 * time and without repetition, using Murty's partitioning.
 *
 * Given the optimal weight W* of the initial perfect matching, only matchings whose total weight
 * equals W* (up to [eps]) are emitted: [next] returns successive distinct optima and finally null
 * once they are exhausted. Distinctness and completeness over the optimal set are guaranteed by the
 * partitioning (each candidate matching belongs to exactly one subproblem region), so a null result
 * is a trustworthy "there is no other optimal matching", not "none found within a budget".
 *
 * Murty's two primitives, on top of a black-box perfect-matching solver:
 *  - *exclude* a pair: drop it from the candidate graph;
 *  - *force* a pair: pre-commit it (remove both endpoints) and add its weight to the baseline.
 *
 * The graph is treated as undirected (one edge per vertex pair). Edge orientation, if any, is the
 * caller's concern.
 */
class PerfectMatchingEnumerator<V>(
    private val graph: Graph<V, DefaultWeightedEdge>,
    private val sense: ObjectiveSense = ObjectiveSense.MAXIMIZE,
    private val eps: Double = KolmogorovWeightedPerfectMatching.EPS
) {
    /** A perfect matching as a set of unordered vertex pairs, with its total weight. */
    data class Matching<V>(val pairs: Set<Set<V>>, val weight: Double)

    private class Region<V>(
        val forced: Set<Set<V>>,
        val forbidden: Set<Set<V>>,
        val matching: Set<Set<V>>,
        val weight: Double
    )

    private val byWeight: Comparator<Region<V>> =
        if (sense == ObjectiveSense.MAXIMIZE) compareByDescending { it.weight }
        else compareBy { it.weight }
    private val queue = PriorityQueue(byWeight)
    private var optimum: Double? = null
    private var started = false

    /**
     * Returns the next distinct optimal matching, or null when all optima have been emitted (or the
     * graph has no perfect matching at all).
     */
    fun next(): Matching<V>? {
        if (!started) {
            started = true
            val root = solve(emptySet(), emptySet()) ?: return null
            optimum = root.weight
            queue.add(root)
        }
        val opt = optimum ?: return null
        val region = queue.poll() ?: return null
        if (!isOptimal(region.weight, opt)) return null
        partition(region)
        return Matching(region.matching, region.weight)
    }

    private fun isOptimal(weight: Double, opt: Double) =
        if (sense == ObjectiveSense.MAXIMIZE) weight >= opt - eps else weight <= opt + eps

    /** Murty partition of a region into disjoint children, each excluding the region's matching. */
    private fun partition(region: Region<V>) {
        var forced = region.forced
        for (pair in region.matching - region.forced) {
            solve(forced, region.forbidden + setOf(pair))?.let { queue.add(it) }
            forced = forced + setOf(pair)
        }
    }

    /** Solves the perfect matching under the given forced/forbidden pair constraints. */
    private fun solve(forced: Set<Set<V>>, forbidden: Set<Set<V>>): Region<V>? {
        val forcedVertices = forced.flatten().toSet()
        val forcedWeight = forced.sumOf { graph.getEdgeWeight(edgeOf(it)) }

        val sub = SimpleWeightedGraph<V, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        for (v in graph.vertexSet()) if (v !in forcedVertices) sub.addVertex(v)
        for (e in graph.edgeSet()) {
            val u = graph.getEdgeSource(e)
            val w = graph.getEdgeTarget(e)
            if (u in forcedVertices || w in forcedVertices) continue
            if (setOf(u, w) in forbidden) continue
            sub.addEdge(u, w)?.let { sub.setEdgeWeight(it, graph.getEdgeWeight(e)) }
        }

        val (matching, weight) = when {
            sub.vertexSet().isEmpty() -> emptySet<Set<V>>() to 0.0
            sub.vertexSet().size % 2 != 0 -> return null
            else -> try {
                val solved = KolmogorovWeightedPerfectMatching(sub, sense).matching
                solved.edges.map { setOf(sub.getEdgeSource(it), sub.getEdgeTarget(it)) }.toSet() to solved.weight
            } catch (e: IllegalArgumentException) {
                return null // no perfect matching under these constraints
            }
        }
        return Region(forced, forbidden, forced + matching, forcedWeight + weight)
    }

    private fun edgeOf(pair: Set<V>): DefaultWeightedEdge {
        val (u, v) = pair.toList()
        return graph.getEdge(u, v) ?: throw IllegalArgumentException("no edge for forced pair $pair")
    }
}
