package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID

/**
 * Direct confrontation placement criteria (DC / SDC), following OpenGotha's semantics
 * (Tournament.defineDirForExAequoGroup + mw.go.confrontation.Confrontation).
 *
 * Both operate on a group of players tied on all placement criteria before the DC/SDC
 * criterion, looking only at games played between group members. Values are group-relative:
 * they only order players within the group (a higher criterion already separates groups).
 */
object DirectConfrontation {

    /**
     * Net decisive victories among [members]: (winner, loser) pairs.
     * Only even games with a plain BLACK/WHITE result count; multiple games between the
     * same two players sum algebraically (a 1-1 split cancels out).
     */
    fun netWins(games: Collection<Game>, members: Set<ID>): Set<Pair<ID, ID>> {
        val net = mutableMapOf<Pair<ID, ID>, Int>()
        games.forEach { game ->
            if (game.handicap != 0) return@forEach
            val winner = when (game.result) {
                Game.Result.BLACK -> game.black
                Game.Result.WHITE -> game.white
                else -> return@forEach
            }
            val loser = if (winner == game.black) game.white else game.black
            if (winner !in members || loser !in members) return@forEach
            val key = Pair(minOf(winner, loser), maxOf(winner, loser))
            net[key] = (net[key] ?: 0) + if (winner == key.first) 1 else -1
        }
        return net.mapNotNull { (pair, sum) ->
            when {
                sum > 0 -> pair
                sum < 0 -> Pair(pair.second, pair.first)
                else -> null
            }
        }.toSet()
    }

    /**
     * Simplified direct confrontation: relevant only when every pair of group members has
     * a decisive net result; then each player scores the number of group members beaten.
     * Otherwise everyone gets 0.
     */
    fun sdc(group: List<ID>, wins: Set<Pair<ID, ID>>): Map<ID, Double> {
        if (group.size <= 1) return group.associateWith { 0.0 }
        val complete = group.indices.all { i ->
            (i + 1 until group.size).all { j ->
                Pair(group[i], group[j]) in wins || Pair(group[j], group[i]) in wins
            }
        }
        if (!complete) return group.associateWith { 0.0 }
        return group.associateWith { p -> wins.count { it.first == p }.toDouble() }
    }

    /**
     * Direct confrontation. [afterKey] gives each player's values for the placement criteria
     * *after* DC (higher = better), used to order players the win graph cannot separate.
     *
     * Victory cycles are neutralized OpenGotha-style: inside a strongly connected component
     * wins are ignored, and every member inherits the component's direct external wins and
     * losses. Then players are iteratively removed from the bottom: among remaining players
     * with no victory left, those with the lowest [afterKey] rank last.
     * Returned values: higher = better, ties share the same value.
     */
    fun dc(group: List<ID>, wins: Set<Pair<ID, ID>>, afterKey: (ID) -> List<Double>): Map<ID, Double> {
        if (group.size <= 1) return group.associateWith { 0.0 }

        val scc = stronglyConnectedComponents(group, wins)
        // beats: expand direct inter-component edges to all members of both components
        val componentBeats = wins.mapNotNull { (w, l) ->
            if (scc[w] != scc[l]) Pair(scc[w]!!, scc[l]!!) else null
        }.toSet()
        val beats = group.associateWith { p ->
            group.filter { q -> Pair(scc[p]!!, scc[q]!!) in componentBeats }.toSet()
        }

        val remaining = group.toMutableSet()
        val rank = mutableMapOf<ID, Int>()
        var maxRank = 0
        while (remaining.isNotEmpty()) {
            val candidates = remaining.filter { p -> beats[p]!!.none { it in remaining } }
            check(candidates.isNotEmpty()) { "direct confrontation: no candidate left" }
            val worstKey = candidates.map(afterKey).minWith(::compareKeys)
            val batch = candidates.filter { compareKeys(afterKey(it), worstKey) == 0 }
            val batchRank = 1 + remaining.size - batch.size
            if (maxRank == 0) maxRank = batchRank
            batch.forEach { rank[it] = batchRank }
            remaining.removeAll(batch.toSet())
        }
        return rank.mapValues { (_, r) -> (maxRank - r).toDouble() }
    }

    private fun compareKeys(left: List<Double>, right: List<Double>): Int {
        for (i in left.indices) {
            val cmp = left[i].compareTo(right.getOrElse(i) { 0.0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    // iterative Tarjan; component ids are arbitrary but distinct
    private fun stronglyConnectedComponents(group: List<ID>, wins: Set<Pair<ID, ID>>): Map<ID, Int> {
        val adjacency = group.associateWith { p -> wins.filter { it.first == p }.map { it.second } }
        val index = mutableMapOf<ID, Int>()
        val lowLink = mutableMapOf<ID, Int>()
        val onStack = mutableSetOf<ID>()
        val stack = ArrayDeque<ID>()
        val component = mutableMapOf<ID, Int>()
        var nextIndex = 0
        var nextComponent = 0

        for (start in group) {
            if (start in index) continue
            // work stack of (node, iterator over successors)
            val work = ArrayDeque<Pair<ID, Iterator<ID>>>()
            index[start] = nextIndex; lowLink[start] = nextIndex; nextIndex++
            stack.addLast(start); onStack.add(start)
            work.addLast(Pair(start, adjacency[start]!!.iterator()))
            while (work.isNotEmpty()) {
                val (node, successors) = work.last()
                var recursed = false
                while (successors.hasNext()) {
                    val next = successors.next()
                    if (next !in index) {
                        index[next] = nextIndex; lowLink[next] = nextIndex; nextIndex++
                        stack.addLast(next); onStack.add(next)
                        work.addLast(Pair(next, adjacency[next]!!.iterator()))
                        recursed = true
                        break
                    } else if (next in onStack) {
                        lowLink[node] = minOf(lowLink[node]!!, index[next]!!)
                    }
                }
                if (recursed) continue
                work.removeLast()
                work.lastOrNull()?.let { (parent, _) ->
                    lowLink[parent] = minOf(lowLink[parent]!!, lowLink[node]!!)
                }
                if (lowLink[node] == index[node]) {
                    do {
                        val member = stack.removeLast()
                        onStack.remove(member)
                        component[member] = nextComponent
                    } while (member != node)
                    nextComponent++
                }
            }
        }
        return component
    }
}
