package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.toID
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.Event
import org.jeudego.pairgoth.server.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PairingHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round > tournament.lastRound() + 1) badRequest("invalid round: previous round has not been played")
        val playing = tournament.games(round).values.flatMap {
            listOf(it.black, it.white)
        }.toSet()
        val unpairables = tournament.pairables.values.filter { it.skip.contains(round) }.sortedByDescending { it.rating }.map { it.id }.toJsonArray()
        val pairables = tournament.pairables.values.filter { !it.skip.contains(round) && !playing.contains(it.id) }.sortedByDescending { it.rating }.map { it.id }.toJsonArray()
        val games = tournament.games(round).values
        return Json.Object(
            "games" to games.map { it.toJson() }.toCollection(Json.MutableArray()),
            "pairables" to pairables,
            "unpairables" to unpairables
        )
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round > tournament.lastRound() + 1) badRequest("invalid round: previous round has not been played")
        val payload = getArrayPayload(request)
        if (payload.isEmpty()) badRequest("nobody to pair")
        val allPlayers = payload.size == 1 && payload[0] == "all"
        //if (!allPlayers && tournament.pairing.type == PairingType.SWISS) badRequest("Swiss pairing requires all pairable players")
        val playing = (tournament.games(round).values).flatMap {
            listOf(it.black, it.white)
        }.toSet()
        val pairables =
            if (allPlayers)
                tournament.pairables.values.filter { !it.skip.contains(round) && !playing.contains(it.id) }
            else payload.map {
                // CB - because of the '["all"]' map, conversion to int lands here... Better API syntax for 'all players'?
                if (it is Number) it.toID() else badRequest("invalid pairable id: #$it")
            }.map { id ->
                tournament.pairables[id]?.also {
                    if (it.skip.contains(round)) badRequest("pairable #$id does not play round $round")
                    if (playing.contains(it.id)) badRequest("pairable #$id already plays round $round")
                } ?: badRequest("invalid pairable id: #$id")
            }
        val games = tournament.pair(round, pairables)
        val ret = games.map { it.toJson() }.toJsonArray()
        tournament.dispatchEvent(gamesAdded, Json.Object("round" to round, "games" to ret))
        return ret
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        // only allow last round (if players have not been paired in the last round, it *may* be possible to be more laxist...)
        if (round != tournament.lastRound()) badRequest("cannot edit pairings in other rounds but the last")
        val payload = getObjectPayload(request)
        val gameId = payload.getInt("id") ?: badRequest("invalid game id")
        val game = tournament.games(round)[gameId] ?: badRequest("invalid game id")
        val playing = (tournament.games(round).values).filter { it.id != gameId }.flatMap {
            listOf(it.black, it.white)
        }.toSet()
        game.black = payload.getID("b") ?: badRequest("missing black player id")
        game.white = payload.getID("w") ?: badRequest("missing white player id")
        tournament.recomputeDUDD(round, game.id)
        // temporary
        payload.getInt("dudd")?.let { game.drawnUpDown = it }
        val black = tournament.pairables[game.black] ?: badRequest("invalid black player id")
        val white = tournament.pairables[game.black] ?: badRequest("invalid white player id")
        if (black.skip.contains(round)) badRequest("black is not playing this round")
        if (white.skip.contains(round)) badRequest("white is not playing this round")
        if (playing.contains(black.id)) badRequest("black is already in another game")
        if (playing.contains(white.id)) badRequest("white is already in another game")
        if (payload.containsKey("h")) game.handicap = payload.getString("h")?.toIntOrNull() ?:  badRequest("invalid handicap")
        tournament.dispatchEvent(gameUpdated, Json.Object("round" to round, "game" to game.toJson()))
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        // only allow last round (if players have not been paired in the last round, it *may* be possible to be more laxist...)
        // Nope
        // if (round != tournament.lastRound()) badRequest("cannot delete games in other rounds but the last")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (allPlayers) {
            tournament.games(round).clear()
        } else {
            payload.forEach {
                val id = (it as Number).toInt()
                val game = tournament.games(round)[id] ?: throw Error("invalid game id")
                if (game.result != Game.Result.UNKNOWN) {
                    ApiHandler.logger.error("cannot unpair game id ${game.id}: it has a result")
                    // we'll only skip it
                    // throw Error("cannot unpair ")
                } else {
                    tournament.games(round).remove(id)
                }
            }
        }
        tournament.dispatchEvent(gamesDeleted, Json.Object("round" to round, "games" to payload))
        return Json.Object("success" to true)
    }
}
