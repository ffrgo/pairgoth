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
    // the EGF "Number of Wins Score": wins, plus what the tournament gives for a non-played round,
    // rounded down. Solvers install the real one; a bare helper falls back on the raw win count.
    var nbwScoresFactory: ScoreMapFactory = { wins }

    val scores by lazy { scoresFactory() }
    val scoresX by lazy { scoresXFactory() }
    val missedRoundsSos by lazy { missedRoundsSosFactory() }
    val nbwScores by lazy { nbwScoresFactory() }

    // EGF game value: a win 1, a jigo ½, a loss 0 (a bye is stored as a win for the real player).
    // Every accumulated score below is built from this single definition.
    protected fun Game.scoreOf(id: ID) = when {
        id != black && id != white -> 0.0
        result == BOTHWIN -> 1.0
        result == JIGO -> 0.5
        result == BLACK -> if (id == black) 1.0 else 0.0
        result == WHITE -> if (id == white) 1.0 else 0.0
        else -> 0.0
    }

    // Generic helper functions
    open fun playedTogether(p1: Pairable, p2: Pairable) = paired.contains(Pair(p1.id, p2.id))
    /** Who held white the last time those two met, null if they never did. */
    open fun lastMeetingWhite(p1: Pairable, p2: Pairable): ID? = lastMeetingWhite[Pair(p1.id, p2.id)]
    open fun colorBalance(p: Pairable) = colorBalance[p.id]
    open fun nbPlayedWithBye(p: Pairable) = nbPlayedWithBye[p.id]

    protected val paired: Set<Pair<ID, ID>> by lazy {
        (history.flatten().map { game ->
            Pair(game.black, game.white)
        } + history.flatten().map { game ->
            Pair(game.white, game.black)
        }).toSet()
    }

    // Colours of the last meeting of two players, both directions: pair -> who held white.
    // Rounds are walked in order, so a later meeting overwrites an earlier one.
    private val lastMeetingWhite: Map<Pair<ID, ID>, ID> by lazy {
        mutableMapOf<Pair<ID, ID>, ID>().apply {
            history.flatten().filter { game ->
                game.white != ByePlayer.id && game.black != ByePlayer.id
            }.forEach { game ->
                put(Pair(game.white, game.black), game.white)
                put(Pair(game.black, game.white), game.white)
            }
        }
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
                listOf(game.black, game.white).forEach { id ->
                    val score = game.scoreOf(id)
                    if (score != 0.0) put(id, getOrDefault(id, 0.0) + score)
                }
            }
        }
    }

    // define mms to be a synonym of scores
    val mms by lazy { scores }

    // Per-round SOS contributions: the opponent's score (handicap-adjusted, OpenGotha-style) for
    // a played game, the player's own pseudo-score for a bye or a missed round (the Mac-Mahon
    // starting score, or 0 in a swiss). One entry per round, so SOS-1 and SOS-2 discard *rounds*.
    private val sosContributions: Map<ID, List<Double>> by lazy {
        val perRound = history.map { games ->
            games.flatMap { game ->
                listOf(
                    game.black to if (game.white == ByePlayer.id) null else scores[game.white]?.let { it - game.handicap } ?: 0.0,
                    game.white to if (game.black == ByePlayer.id) null else scores[game.black]?.let { it + game.handicap } ?: 0.0
                )
            }.toMap()
        }
        missedRoundsSos.mapValues { (id, pseudoSos) ->
            perRound.map { round -> round[id] ?: pseudoSos }
        }
    }

    val sos by lazy { sosContributions.mapValues { (_, rounds) -> rounds.sum() } }

    // sos-1 and sos-2: SOS ignoring the 1 (resp. 2) rounds of smallest value
    val sosm1 by lazy { sosContributions.mapValues { (_, rounds) -> rounds.sorted().drop(1).sum() } }

    val sosm2 by lazy { sosContributions.mapValues { (_, rounds) -> rounds.sorted().drop(2).sum() } }

    // sodos — the opponent's score weighted by the game value, so a jigo brings half of it
    val sodos by lazy {
        (history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.black, game.scoreOf(game.black) * (scores[game.white]?.let { it - game.handicap } ?: 0.0))
        } + history.flatten().filter { game ->
            game.white != 0 // Remove games against byePlayer
        }.map { game ->
            Pair(game.white, game.scoreOf(game.white) * (scores[game.black]?.let { it + game.handicap } ?: 0.0))
        }).groupingBy { it.first }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }


    // wins-based flavors backing the SOSW/SOSOSW/SODOSW criteria: Swiss semantics whatever the
    // tournament type — opponents' Number of Wins Score, no handicap adjustment, byes and missed
    // rounds worth 0. In a no-handicap Swiss they coincide with the score-based maps; in MacMahon
    // they give handicap-free tie-breaks (NBW-ranked "swiss with handicap" played as MM).
    // Per-round contributions, as sosContributions; players who never played get no entry at all.
    private val winsSosContributions: Map<ID, List<Double>> by lazy {
        val perRound = history.map { games ->
            games.flatMap { game ->
                listOf(
                    game.black to if (game.white == ByePlayer.id) 0.0 else nbwScores[game.white] ?: 0.0,
                    game.white to if (game.black == ByePlayer.id) 0.0 else nbwScores[game.black] ?: 0.0
                )
            }.toMap()
        }
        playersPerRound.flatten().toSet().associateWith { id ->
            perRound.map { round -> round[id] ?: 0.0 }
        }
    }

    val winsSos: Map<ID, Double> by lazy {
        winsSosContributions.mapValues { (_, rounds) -> rounds.sum() }
    }

    // minus the n rounds of smallest value
    private fun winsSosMinus(n: Int) = winsSosContributions.mapValues { (_, rounds) ->
        rounds.sorted().drop(n).sum()
    }

    val winsSosm1: Map<ID, Double> by lazy { winsSosMinus(1) }
    val winsSosm2: Map<ID, Double> by lazy { winsSosMinus(2) }

    val winsSosos: Map<ID, Double> by lazy {
        (history.flatten().map { game ->
            Pair(game.black, if (game.white == ByePlayer.id) 0.0 else winsSos[game.white] ?: 0.0)
        } + history.flatten().map { game ->
            Pair(game.white, if (game.black == ByePlayer.id) 0.0 else winsSos[game.black] ?: 0.0)
        }).groupingBy {
            it.first
        }.fold(0.0) { acc, next ->
            acc + next.second
        }
    }

    val winsSodos: Map<ID, Double> by lazy {
        (history.flatten().filter { game ->
            game.white != ByePlayer.id && game.black != ByePlayer.id
        }.flatMap { game ->
            listOf(
                Pair(game.black, game.scoreOf(game.black) * (nbwScores[game.white] ?: 0.0)),
                Pair(game.white, game.scoreOf(game.white) * (nbwScores[game.black] ?: 0.0))
            )
        }).groupingBy {
            it.first
        }.fold(0.0) { acc, next ->
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


    // cumulative score: the sum, over every round, of the score standing at the end of that round
    val cumScore by lazy {
        val running = mutableMapOf<ID, Double>()
        val cumulative = mutableMapOf<ID, Double>()
        history.forEach { games ->
            games.forEach { game ->
                listOf(game.black, game.white).filter { it != ByePlayer.id }.forEach { id ->
                    running[id] = running.getOrDefault(id, 0.0) + game.scoreOf(id)
                }
            }
            running.forEach { (id, total) -> cumulative[id] = cumulative.getOrDefault(id, 0.0) + total }
        }
        cumulative
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
