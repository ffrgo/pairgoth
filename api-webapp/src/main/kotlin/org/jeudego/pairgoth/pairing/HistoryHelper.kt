package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.Game.Result.*

open class HistoryHelper(
    protected val history: List<List<Game>>,
    // scoresGetter() returns Pair(sos value for missed rounds, score) where score is nbw for Swiss, mms for MM, ...
    scoresGetter: HistoryHelper.()-> Map<ID, Pair<Double, Double>>) {

    private val Game.blackScore get() = when (result) {
        BLACK, BOTHWIN -> 1.0
        else -> 0.0
    }

    private val Game.whiteScore get() = when (result) {
        WHITE, BOTHWIN -> 1.0
        else -> 0.0
    }

    private val scores by lazy {
        scoresGetter()
    }

    val scoresX by lazy {
        scoresGetter().mapValues { entry ->
            entry.value.first + (wins[entry.key] ?: 0.0)
        }
    }

    // Generic helper functions
    open fun playedTogether(p1: Pairable, p2: Pairable) = paired.contains(Pair(p1.id, p2.id))
    open fun colorBalance(p: Pairable) = colorBalance[p.id]
    open fun nbPlayedWithBye(p: Pairable) = nbPlayedWithBye[p.id]
    open fun nbW(p: Pairable) = wins[p.id]

    fun drawnUpDown(p: Pairable) = drawnUpDown[p.id]

    protected val paired: Set<Pair<ID, ID>> by lazy {
        (history.flatten().map { game ->
            Pair(game.black, game.white)
        } + history.flatten().map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // Returns the number of games played as white minus the number of games played as black
    // Only count games without handicap
    private val colorBalance: Map<ID, Int> by lazy {
        history.flatten().filter { game ->
            game.handicap == 0
        }.filter { game ->
            game.white != ByePlayer.id && game.black != ByePlayer.id // Remove games against byePlayer
        }.flatMap { game ->
            listOf(Pair(game.white, +1), Pair(game.black, -1))
        }.groupingBy {
            it.first
        }.fold(0) { acc, next ->
            acc + next.second
        }
    }

    private val nbPlayedWithBye: Map<ID, Int> by lazy {
        history.flatten().flatMap { game ->
            // Duplicates (white, black) into (white, black) and (black, white)
            listOf(Pair(game.white, game.black), Pair(game.black, game.white))
        }.groupingBy {
            it.first
        }.fold(0) { acc, next ->
            acc + if (next.second == ByePlayer.id) 1 else 0
        }
    }

    // Set of all implied players for each round (warning: does comprise games with BIP)
    val playersPerRound: List<Set<ID>> by lazy {
        history.map {
            it.fold(mutableSetOf<ID>()) { acc, next ->
                if(next.white != 0) acc.add(next.white)
                if (next.black != 0) acc.add(next.black)
                acc
            }
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

    // define mms to be a synonym of scores
    val mms by lazy { scores.mapValues { it -> it.value.second } }

    val sos by lazy {
        val historySos = (history.flatten().map { game ->
            Pair(
                game.black,
                if (game.white == 0) scores[game.black]?.first ?: 0.0
                else scores[game.white]?.second?.let { it - game.handicap } ?: 0.0
            )
        } + history.flatten().map { game ->
            Pair(
                game.white,
                if (game.black == 0) scores[game.white]?.first ?: 0.0
                else scores[game.black]?.second?.let { it + game.handicap } ?: 0.0
            )
        }).groupingBy {
            it.first
        }.fold(0.0) { acc, next ->
            acc + next.second
        }

        scores.mapValues { (id, pair) ->
            (historySos[id] ?: 0.0) + playersPerRound.sumOf {
                if (it.contains(id)) 0.0 else pair.first
            }

        }
    }

    // sos-1
    val sosm1 by lazy {
        (history.flatten().map { game ->
            Pair(game.black, scores[game.white]?.second?.let { it - game.handicap } ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, scores[game.black]?.second?.let { it + game.handicap } ?: 0.0)
        }).groupBy {
            it.first
        }.mapValues { (id, pairs) ->
          val oppScores = pairs.map { it.second }.sortedDescending()
          oppScores.sum() - (oppScores.firstOrNull() ?: 0.0) +
              playersPerRound.sumOf { players ->
                  if (players.contains(id)) 0.0
                  else scores[id]?.first ?: 0.0
              }
        }
    }

    // sos-2
    val sosm2 by lazy {
        (history.flatten().map { game ->
            Pair(game.black, scores[game.white]?.second?.let { it - game.handicap } ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, scores[game.black]?.second?.let { it + game.handicap } ?: 0.0)
        }).groupBy {
            it.first
        }.mapValues { (id, pairs) ->
            val oppScores = pairs.map { it.second }.sorted()
            oppScores.sum() - oppScores.getOrElse(0) { 0.0 } - oppScores.getOrElse(1) { 0.0 } +
                playersPerRound.sumOf { players ->
                    if (players.contains(id)) 0.0
                    else scores[id]?.first ?: 0.0
                }
        }
    }

    // sodos
    val sodos by lazy {
        (history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.black, if (game.result == Game.Result.BLACK) scores[game.white]?.second?.let { it - game.handicap } ?: 0.0 else 0.0)
        } + history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.white, if (game.result == Game.Result.WHITE) scores[game.black]?.second?.let { it + game.handicap } ?: 0.0 else 0.0)
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    // sosos
    val sosos by lazy {
        val currentRound = history.size
        val historySosos = (history.flatten().map { game ->
            Pair(game.black, sos[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, sos[game.black] ?: 0.0)
        }).groupingBy {
            it.first
        }.fold(0.0) { acc, next ->
            acc + next.second
        }

        scores.mapValues { (id, pair) ->
            (historySosos[id] ?: 0.0) + playersPerRound.sumOf {
                if (it.contains(id)) 0.0 else pair.first * currentRound
            }

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

    // drawn up down: map ID -> Pair(sum of drawn up, sum of drawn down)
    val drawnUpDown by lazy {
        (history.flatten().map { game ->
            Pair(game.white, Pair(
                Math.max(0, game.drawnUpDown),
                Math.max(0, -game.drawnUpDown)
            ))
        } + history.flatten().map { game ->
            Pair(game.black, Pair(
                Math.max(0, -game.drawnUpDown),
                Math.max(0, game.drawnUpDown)
            ))
        }).groupingBy { it.first }.fold(Pair(0, 0)) { acc, next ->
            Pair(acc.first + next.second.first, acc.second + next.second.second)
        }
    }

    val byePlayers by lazy {
        history.flatten().mapNotNull { game ->
            if (game.white == ByePlayer.id) game.black
            else if (game.black == ByePlayer.id) game.white
            else null
        }
    }

}

// CB TODO - a big problem with the current naive implementation is that the team score is -for now- the sum of team members individual scores

class TeamOfIndividualsHistoryHelper(history: List<List<Game>>, scoresGetter: () -> Map<ID, Pair<Double, Double>>):
        HistoryHelper(history, { scoresGetter() }) {

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
