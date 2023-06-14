package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

open class HistoryHelper(protected val history: List<Game>, computeScore: () -> Map<ID, Double>) {

    fun getCriterionValue(p: Pairable, crit: PlacementCriterion): Double {
        // Returns generic criterion
        // Specific criterion are computed by solvers directly
        return when (crit) {
            PlacementCriterion.NULL -> 0.0
            PlacementCriterion.CATEGORY -> TODO()
            PlacementCriterion.RANK -> p.rank.toDouble()
            PlacementCriterion.RATING -> p.rating.toDouble()

            PlacementCriterion.EXT -> TODO()
            PlacementCriterion.EXR -> TODO()
            PlacementCriterion.SDC -> TODO()
            PlacementCriterion.DC -> TODO()
            else -> -1.0
        }
    }
    // Generic helper functions
    open fun playedTogether(p1: Pairable, p2: Pairable) = paired.contains(Pair(p1.id, p2.id))
    open fun colorBalance(p: Pairable) = colorBalance[p.id]
    open fun nbW(p: Pairable) = numberWins[p.id]


    protected val paired: Set<Pair<ID, ID>> by lazy {
        (history.map { game ->
            Pair(game.black, game.white)
        } + history.map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // Returns the number of games played as white
    // Only count games without handicap
    private val colorBalance: Map<ID, Int> by lazy {
        history.flatMap { game -> if (game.handicap == 0) {
            listOf(Pair(game.white, +1), Pair(game.black, -1))
        } else {
            listOf(Pair(game.white, 0), Pair(game.black, 0))
        }
        }.groupingBy { it.first }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    val numberWins: Map<ID, Double> by lazy {
        mutableMapOf<ID, Double>().apply {
            history.forEach { game ->
                when (game.result) {
                    Game.Result.BLACK -> put(game.black, getOrDefault(game.black, 0.0) + 1.0)
                    Game.Result.WHITE -> put(game.white, getOrDefault(game.white, 0.0) + 1.0)
                    Game.Result.BOTHWIN -> {
                        put(game.black, getOrDefault(game.black, 0.0) + 0.5)
                        put(game.white, getOrDefault(game.white, 0.0) + 0.5)
                    }
                    else -> {}
                }
            }
        }
    }

    // Has to be set after construction
    private val score by lazy {
        computeScore()
    }

    // SOS related functions given a score function
    val sos by lazy {
        (history.map { game ->
            Pair(game.black, score[game.white] ?: 0.0)
        } + history.map { game ->
            Pair(game.white, score[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sos-1
    val sosm1: Map<ID, Double> by lazy {
        TODO()
    }

    // sos-2
    val sosm2: Map<ID, Double> by lazy {
        TODO()
    }

    // sodos
    val sodos by lazy {
        (history.map { game ->
            Pair(game.black, if (game.result == Game.Result.BLACK) score[game.white] ?: 0.0 else 0.0)
        } + history.map { game ->
            Pair(game.white, if (game.result == Game.Result.WHITE) score[game.black] ?: 0.0 else 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sosos
    val sosos by lazy {
        (history.map { game ->
            Pair(game.black, sos[game.white] ?: 0.0)
        } + history.map { game ->
            Pair(game.white, sos[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // cumulative score
    val cumscore: Map<ID, Double> by lazy {
        TODO()
    }
}

// CB TODO - a big problem with the current naive implementation is that the team score is -for now- the sum of team members individual scores

class TeamOfIndividualsHistoryHelper(history: List<Game>, computeScore: () -> Map<ID, Double>):
        HistoryHelper(history, computeScore) {

    private fun Pairable.asTeam() = this as TeamTournament.Team

    override fun playedTogether(p1: Pairable, p2: Pairable) = paired.intersect(p1.asTeam().playerIds.first().let { id ->
        (p2.asTeam()).playerIds.map {Pair(it, id) }
    }.toSet()).isNotEmpty()

    override fun nbW(p: Pairable) = p.asTeam().teamPlayers.map { super.nbW(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sos(p:Pairable) = p.asTeam().teamPlayers.map { super.sos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sosos(p:Pairable) = p.asTeam().teamPlayers.map { super.sosos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sodos(p:Pairable) = p.asTeam().teamPlayers.map { super.sodos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
}
