package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.model.Game.Result.*
import org.jeudego.pairgoth.pairing.solver.Solver

typealias ScoreMap = Map<ID, Double>
typealias ScoreMapFactory = () -> ScoreMap

open class HistoryHelper(
    protected val history: List<List<Game>>
) {

    lateinit var scoresFactory: ScoreMapFactory
    lateinit var scoresXFactory: ScoreMapFactory
    lateinit var missedRoundsSosFactory: ScoreMapFactory

    val scores by lazy { scoresFactory() }
    val scoresX by lazy { scoresXFactory() }
    val missedRoundsSos by lazy { missedRoundsSosFactory() }

    private val Game.blackScore get() = when (result) {
        BLACK, BOTHWIN -> 1.0
        else -> 0.0
    }

    private val Game.whiteScore get() = when (result) {
        WHITE, BOTHWIN -> 1.0
        else -> 0.0
    }

    // Generic helper functions
    open fun playedTogether(p1: Pairable, p2: Pairable) = paired.contains(Pair(p1.id, p2.id))
    open fun colorBalance(p: Pairable) = colorBalance[p.id]
    open fun nbPlayedWithBye(p: Pairable) = nbPlayedWithBye[p.id]

    protected val paired: Set<Pair<ID, ID>> by lazy {
        (history.flatten().map { game ->
            Pair(game.black, game.white)
        } + history.flatten().map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // Returns the number of games played as white minus the number of games played as black
    // Only count games without handicap
    val colorBalance: Map<ID, Int> by lazy {
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

    // Set of all implied players for each round
    val playersPerRound: List<Set<ID>> by lazy {
        history.map { roundGames ->
            roundGames.flatMap {
                game -> listOf(game.white, game.black)
            }.filter { id ->
                id != ByePlayer.id
            }.toSet()
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
    val mms by lazy { scores }

    val sos by lazy {
        // SOS for played games against a real opponent or BIP
        val historySos = (history.flatten().map { game ->
            Pair(
                game.black,
                if (game.white == 0) missedRoundsSos[game.black] ?: 0.0
                else scores[game.white]?.let { it - game.handicap } ?: 0.0
            )
        } + history.flatten().map { game ->
            Pair(
                game.white,
                if (game.black == 0) missedRoundsSos[game.white] ?: 0.0
                else scores[game.black]?.let { it + game.handicap } ?: 0.0
            )
        }).groupingBy {
            it.first
        }.fold(0.0) { acc, next ->
            acc + next.second
        }
        // plus SOS for missed rounds
        missedRoundsSos.mapValues { (id, pseudoSos) ->
            (historySos[id] ?: 0.0) + playersPerRound.sumOf {
                if (it.contains(id)) 0.0 else pseudoSos
            }
        }
    }

    // sos-1
    val sosm1 by lazy {
        // SOS for played games against a real opponent or BIP
        (history.flatten().map { game ->
            Pair(
                game.black,
                if (game.white == 0) missedRoundsSos[game.black] ?: 0.0
                else scores[game.white]?.let { it - game.handicap } ?: 0.0
            )
        } + history.flatten().map { game ->
            Pair(
                game.white,
                if (game.black == 0) missedRoundsSos[game.white] ?: 0.0
                else scores[game.black]?.let { it + game.handicap } ?: 0.0
            )
        }).groupBy {
            it.first
        }.mapValues { (id, pairs) ->
          val oppScores = pairs.map { it.second }.sortedDescending()
            // minus greatest SOS
          oppScores.sum() - (oppScores.firstOrNull() ?: 0.0) +
              // plus SOS for missed rounds
              playersPerRound.sumOf { players ->
                  if (players.contains(id)) 0.0
                  else missedRoundsSos[id] ?: 0.0
              }
        }
    }

    // sos-2
    val sosm2 by lazy {
        // SOS for played games against a real opponent or BIP
        (history.flatten().map { game ->
            Pair(
                game.black,
                if (game.white == 0) missedRoundsSos[game.black] ?: 0.0
                else scores[game.white]?.let { it - game.handicap } ?: 0.0
            )
        } + history.flatten().map { game ->
            Pair(
                game.white,
                if (game.black == 0) missedRoundsSos[game.white] ?: 0.0
                else scores[game.black]?.let { it + game.handicap } ?: 0.0
            )
        }).groupBy {
            it.first
        }.mapValues { (id, pairs) ->
            val oppScores = pairs.map { it.second }.sortedDescending()
            // minus two greatest SOS
            oppScores.sum() - oppScores.getOrElse(0) { 0.0 } - oppScores.getOrElse(1) { 0.0 } +
                // plus SOS for missed rounds
                playersPerRound.sumOf { players ->
                    if (players.contains(id)) 0.0
                    else missedRoundsSos[id] ?: 0.0
                }
        }
    }

    // sodos
    val sodos by lazy {
        (history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.black, if (game.result == Game.Result.BLACK) scores[game.white]?.let { it - game.handicap } ?: 0.0 else 0.0)
        } + history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.white, if (game.result == Game.Result.WHITE) scores[game.black]?.let { it + game.handicap } ?: 0.0 else 0.0)
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

        missedRoundsSos.mapValues { (id, missedRoundSos) ->
            (historySosos[id] ?: 0.0) + playersPerRound.sumOf {
                if (it.contains(id)) 0.0 else missedRoundSos * currentRound
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
                Math.max(0, -game.drawnUpDown),
                Math.max(0, game.drawnUpDown)
            ))
        } + history.flatten().map { game ->
            Pair(game.black, Pair(
                Math.max(0, game.drawnUpDown),
                Math.max(0, -game.drawnUpDown)
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
