package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.pairing.solver.PerfectMatchingEnumerator
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleWeightedGraph
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerfectMatchingEnumeratorTest {

    private fun graph(vararg edges: Triple<String, String, Double>): SimpleWeightedGraph<String, DefaultWeightedEdge> {
        val g = SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        edges.forEach { (a, b, _) -> g.addVertex(a); g.addVertex(b) }
        edges.forEach { (a, b, w) -> g.setEdgeWeight(g.addEdge(a, b), w) }
        return g
    }

    private fun <V> drain(g: SimpleWeightedGraph<V, DefaultWeightedEdge>): List<PerfectMatchingEnumerator.Matching<V>> {
        val enumerator = PerfectMatchingEnumerator(g)
        val out = mutableListOf<PerfectMatchingEnumerator.Matching<V>>()
        while (true) out.add(enumerator.next() ?: break)
        return out
    }

    private fun <V> assertAllOptimalAndDistinct(matchings: List<PerfectMatchingEnumerator.Matching<V>>) {
        if (matchings.isEmpty()) return
        val opt = matchings.first().weight
        matchings.forEach { assertEquals(opt, it.weight, 1e-9, "non-optimal matching emitted") }
        assertEquals(matchings.size, matchings.map { it.pairs }.toSet().size, "duplicate matching emitted")
    }

    @Test
    fun `4-cycle uniform yields 2 optima`() {
        val g = graph(Triple("a", "b", 1.0), Triple("b", "c", 1.0), Triple("c", "d", 1.0), Triple("d", "a", 1.0))
        val all = drain(g)
        assertAllOptimalAndDistinct(all)
        assertEquals(2, all.size)
    }

    @Test
    fun `K4 uniform yields 3 optima`() {
        val g = graph(
            Triple("a", "b", 1.0), Triple("a", "c", 1.0), Triple("a", "d", 1.0),
            Triple("b", "c", 1.0), Triple("b", "d", 1.0), Triple("c", "d", 1.0)
        )
        val all = drain(g)
        assertAllOptimalAndDistinct(all)
        assertEquals(3, all.size)
    }

    @Test
    fun `two disjoint 4-cycles uniform yield 4 optima`() {
        val g = graph(
            Triple("a", "b", 1.0), Triple("b", "c", 1.0), Triple("c", "d", 1.0), Triple("d", "a", 1.0),
            Triple("e", "f", 1.0), Triple("f", "g", 1.0), Triple("g", "h", 1.0), Triple("h", "e", 1.0)
        )
        val all = drain(g)
        assertAllOptimalAndDistinct(all)
        assertEquals(4, all.size)
    }

    @Test
    fun `unique optimum yields 1`() {
        // {ab,cd}=20 strictly beats {ac,bd}=2 and {ad,bc}=2 under MAXIMIZE
        val g = graph(
            Triple("a", "b", 10.0), Triple("c", "d", 10.0),
            Triple("a", "c", 1.0), Triple("b", "d", 1.0), Triple("a", "d", 1.0), Triple("b", "c", 1.0)
        )
        val all = drain(g)
        assertEquals(1, all.size)
        assertEquals(setOf(setOf("a", "b"), setOf("c", "d")), all.first().pairs)
    }

    @Test
    fun `no perfect matching yields none`() {
        val g = graph(Triple("a", "b", 1.0), Triple("b", "c", 1.0), Triple("c", "a", 1.0))
        assertTrue(drain(g).isEmpty())
    }
}
