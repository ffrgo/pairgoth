package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.Game.Result.*

open class HistoryHelper(protected val history: List<List<Game>>, scoresGetter: ()-> Map<ID, Double>) {

    private val Game.blackScore get() = when (result) {
        BLACK, BOTHWIN -> 1.0
        else -> 0.0
    }

    private val Game.whiteScore get() = when (result) {
        WHITE, BOTHWIN -> 1.0
        else -> 0.0
    }

    private val scores by lazy(scoresGetter)

    // Generic helper functions
    open fun playedTogether(p1: Pairable, p2: Pairable) = paired.contains(Pair(p1.id, p2.id))
    open fun colorBalance(p: Pairable) = colorBalance[p.id]
    open fun nbW(p: Pairable) = wins[p.id]

    protected val paired: Set<Pair<ID, ID>> by lazy {
        (history.flatten().map { game ->
            Pair(game.black, game.white)
        } + history.flatten().map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // Returns the number of games played as white
    // Only count games without handicap
    private val colorBalance: Map<ID, Int> by lazy {
        history.flatten().filter { game ->
            game.handicap == 0
        }.flatMap { game ->
            listOf(Pair(game.white, +1), Pair(game.black, -1))
        }.groupingBy {
            it.first
        }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    val wins: Map<ID, Double> by lazy {
        mutableMapOf<ID, Double>().apply {
            history.flatten().forEach { game ->
                when (game.result) {
                    Game.Result.BLACK -> put(game.black, getOrDefault(game.black, 0.0) + 1.0)
                    Game.Result.WHITE -> put(game.white, getOrDefault(game.white, 0.0) + 1.0)
                    Game.Result.BOTHWIN -> {
                        put(game.black, getOrDefault(game.black, 0.0) + 1.0)
                        put(game.white, getOrDefault(game.white, 0.0) + 1.0)
                    }
                    else -> {}
                }
            }
        }
    }

    // SOS related functions given a score function
    val sos by lazy {
        (history.flatten().map { game ->
            Pair(game.black, scores[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, scores[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sos-1
    val sosm1 by lazy {
        (history.flatten().map { game ->
            Pair(game.black, scores[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, scores[game.black] ?: 0.0)
        }).groupBy {
            it.first
        }.mapValues {
          val scores = it.value.map { it.second }.sorted()
          scores.sum() - (scores.firstOrNull() ?: 0.0)
        }
    }

    // sos-2
    val sosm2 by lazy {
        (history.flatten().map { game ->
            Pair(game.black, scores[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, scores[game.black] ?: 0.0)
        }).groupBy {
            it.first
        }.mapValues {
            val scores = it.value.map { it.second }.sorted()
            scores.sum() - scores.getOrElse(0) { 0.0 } - scores.getOrElse(1) { 0.0 }
        }
    }

    // sodos
    val sodos by lazy {
        (history.flatten().map { game ->
            Pair(game.black, if (game.result == Game.Result.BLACK) scores[game.white] ?: 0.0 else 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, if (game.result == Game.Result.WHITE) scores[game.black] ?: 0.0 else 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sosos
    val sosos by lazy {
        (history.flatten().map { game ->
            Pair(game.black, sos[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, sos[game.black] ?: 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // cumulative score
    val cumScore by lazy {
        history.map { games ->
            (games.groupingBy { it.black }.fold(0.0) { acc, next ->
                acc + next.blackScore
            }) +
            (games.groupingBy { it.white }.fold(0.0) { acc, next ->
                acc + next.whiteScore
            })
        }.reduce { acc, map ->
            (acc.keys + map.keys).associateWith { id -> acc.getOrDefault(id, 0.0) + acc.getOrDefault(id, 0.0) + map.getOrDefault(id, 0.0) }
                .toMap()
        }
    }
}

// CB TODO - a big problem with the current naive implementation is that the team score is -for now- the sum of team members individual scores

class TeamOfIndividualsHistoryHelper(history: List<List<Game>>, scoresGetter: () -> Map<ID, Double>):
        HistoryHelper(history, scoresGetter) {

    private fun Pairable.asTeam() = this as TeamTournament.Team

    override fun playedTogether(p1: Pairable, p2: Pairable) = paired.intersect(p1.asTeam().playerIds.first().let { id ->
        (p2.asTeam()).playerIds.map {Pair(it, id) }
    }.toSet()).isNotEmpty()

    override fun nbW(p: Pairable) = p.asTeam().teamPlayers.map { super.nbW(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sos(p:Pairable) = p.asTeam().teamPlayers.map { super.sos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sosos(p:Pairable) = p.asTeam().teamPlayers.map { super.sosos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()
    //override fun sodos(p:Pairable) = p.asTeam().teamPlayers.map { super.sodos(it) ?: throw Error("unknown player id: #${it.id}") }.sum()

    // TODO CB - now that we've got the rounds in history helper, calculate virtual scores
    // also - try to factorize a bit calculations
}
