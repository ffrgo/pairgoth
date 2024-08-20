package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.server.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PlayerHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> tournament.players.values.map { it.toJson() }.toJsonArray()
            else -> tournament.players[pid]?.toJson() ?: badRequest("no player with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val payload = getObjectPayload(request)
        val player = Player.fromJson(payload)
        player.externalIds.forEach { dbId, pId ->
            if (tournament.hasPlayer(dbId, pId)) badRequest("player already registered")
        }
        tournament.players[player.id] = player
        tournament.dispatchEvent(PlayerAdded, request, player.toJson())
        return Json.Object("success" to true, "id" to player.id)
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val player = tournament.players[id] ?: badRequest("invalid player id")
        val payload = getObjectPayload(request)
        val updated = Player.fromJson(payload, player)
        // check coherence
        if (player.final && !updated.final && tournament.pairedPlayers().contains(updated.id)) {
            badRequest("player is playing")
        }
        val leavingRounds = updated.skip.toSet().minus(player.skip.toSet())
        leavingRounds.forEach {  round ->
            if (round <= tournament.lastRound()) {
                val playing = tournament.games(round).values.flatMap { listOf(it.black, it.white) }
                if (playing.contains(id)) {
                    badRequest("player is playing in round #$round")
                }
            }
        }
        tournament.players[id] = updated
        tournament.dispatchEvent(PlayerUpdated, request, player.toJson())
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        // check coherence
        val player = tournament.players[id] ?: badRequest("invalid player id")
        if (player.final && tournament.pairedPlayers().contains(id)) {
            badRequest("player is playing")
        }
        tournament.players.remove(id) ?: badRequest("invalid player id")
        tournament.dispatchEvent(PlayerDeleted, request, Json.Object("id" to id))
        return Json.Object("success" to true)
    }
}
