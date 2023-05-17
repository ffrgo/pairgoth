package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object PairingHandler: ApiHandler {

    private fun getTournament(request: HttpServletRequest): Tournament {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: badRequest("invalid tournament id")
        return Store.getTournament(tournamentId) ?: badRequest("unknown tournament id")
    }

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        return tournament.pairables.values.filter { !it.skip.contains(round) }.map { it.toJson() }.toJsonArray()
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (!allPlayers && tournament.pairing.type == Pairing.PairingType.SWISS) badRequest("Swiss pairing requires all pairable players")
        val pairables =
            if (allPlayers)
                tournament.pairables.values.filter { !it.skip.contains(round) }
            else payload.map {
                if (it is Number) it.toInt() else badRequest("invalid pairable id: #$it")
            }.map { id ->
                tournament.pairables[id]?.also {
                    if (it.skip.contains(round)) badRequest("pairable #$id does not play round $round")
                } ?: badRequest("invalid pairable id: #$id")
            }
        // check players are not already implied in games
        val pairablesIDs = pairables.map { it.id }.toSet()
        tournament.games.getOrNull(round)?.let { games ->
            games.values.mapNotNull { game ->
                if (pairablesIDs.contains(game.black)) game.black else if (pairablesIDs.contains(game.white)) game.white else null
            }.let {
                if (it.isNotEmpty()) badRequest("The following players are already playing this round: ${it.joinToString { id -> "#${id}" }}")
            }
        }
        val games = tournament.pair(round, pairables)
        return games.map { it.toJson() }.toJsonArray()
    }
}
