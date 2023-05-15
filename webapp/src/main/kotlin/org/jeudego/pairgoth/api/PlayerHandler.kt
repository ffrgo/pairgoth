package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.fromJson
import javax.servlet.http.HttpServletRequest

object PlayerHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request) ?: badRequest("invalid tournament")
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> Json.Array(tournament.pairables.values.map { it.toJson() })
            else -> tournament.pairables[pid]?.toJson() ?: badRequest("no player with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request) ?: badRequest("invalid tournament")
        val payload = getObjectPayload(request)
        // player parsing (CB TODO - team handling, based on tournament type)
        val player = Player.fromJson(payload)
        // CB TODO - handle concurrency
        tournament.pairables[player.id] = player
        // CB TODO - handle event broadcasting
        return Json.Object("success" to true, "id" to player.id)
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request) ?: badRequest("invalid tournament")
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val player = tournament.pairables[id] ?: badRequest("invalid player id")
        val payload = getObjectPayload(request)
        val updated = Player.fromJson(payload, player as Player)
        tournament.pairables[updated.id] = updated
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        return super.delete(request)
    }
}
