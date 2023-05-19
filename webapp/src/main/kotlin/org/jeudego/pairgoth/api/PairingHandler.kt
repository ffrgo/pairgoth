package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object PairingHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val playing = (tournament.games.getOrNull(round)?.values ?: emptyList()).flatMap {
            listOf(it.black, it.white)
        }.toSet()
        return tournament.pairables.values.filter { !it.skip.contains(round) && !playing.contains(it.id) }.toJsonArray()
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (!allPlayers && tournament.pairing.type == Pairing.PairingType.SWISS) badRequest("Swiss pairing requires all pairable players")
        val playing = (tournament.games.getOrNull(round)?.values ?: emptyList()).flatMap {
            listOf(it.black, it.white)
        }.toSet()
        val pairables =
            if (allPlayers)
                tournament.pairables.values.filter { !it.skip.contains(round) && !playing.contains(it.id) }
            else payload.map {
                // CB - because of the '["all"]' map, conversion to int lands here... Better API syntax for 'all players'?
                if (it is Number) it.toInt() else badRequest("invalid pairable id: #$it")
            }.map { id ->
                tournament.pairables[id]?.also {
                    if (it.skip.contains(round)) badRequest("pairable #$id does not play round $round")
                    if (playing.contains(it.id)) badRequest("pairable #$id already plays round $round")
                } ?: badRequest("invalid pairable id: #$id")
            }
        val games = tournament.pair(round, pairables)
        return games.map { it.toJson() }.toJsonArray()
    }
}