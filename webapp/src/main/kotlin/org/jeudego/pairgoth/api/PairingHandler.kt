package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.toID
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.web.Event
import org.jeudego.pairgoth.web.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PairingHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val playing = tournament.games(round).values.flatMap {
            listOf(it.black, it.white)
        }.toSet()
        return tournament.pairables.values.filter { !it.skip.contains(round) && !playing.contains(it.id) }.map { it.id }.toJsonArray()
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (!allPlayers && tournament.pairing.type == Pairing.PairingType.SWISS) badRequest("Swiss pairing requires all pairable players")
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
        Event.dispatch(gamesAdded, Json.Object("tournament" to tournament.id, "round" to round, "data" to ret))
        return ret
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        // only allow last round (if players have not been paired in the last round, it *may* be possible to be more laxist...)
        if (round != tournament.lastRound()) badRequest("cannot edit pairings in other rounds but the last")
        val payload = getObjectPayload(request)
        val game = tournament.games(round)[payload.getInt("id")] ?: badRequest("invalid game id")
        game.black = payload.getID("b") ?: badRequest("missing black player id")
        game.white = payload.getID("w") ?: badRequest("missing white player id")
        if (payload.containsKey("h")) game.handicap = payload.getString("h")?.toIntOrNull() ?:  badRequest("invalid handicap")
        Event.dispatch(gameUpdated, Json.Object("tournament" to tournament.id, "round" to round, "data" to game.toJson()))
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        // only allow last round (if players have not been paired in the last round, it *may* be possible to be more laxist...)
        if (round != tournament.lastRound()) badRequest("cannot delete games in other rounds but the last")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (allPlayers) {
            tournament.games(round).clear()
        } else {
            payload.forEach {
                val id = (it as Number).toInt()
                tournament.games(round).remove(id)
            }
        }
        Event.dispatch(gamesDeleted, Json.Object("tournament" to tournament.id, "round" to round, "data" to payload))
        return Json.Object("success" to true)
    }
}
