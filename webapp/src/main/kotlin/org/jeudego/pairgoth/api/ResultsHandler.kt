package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object ResultsHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val games = tournament.games.getOrNull(round)?.values ?: emptyList()
        return games.map { it.toJson() }.toJsonArray()
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getObjectPayload(request)
        val game = tournament.games[round][payload.getInt("id")] ?: badRequest("invalid game id")
        game.result = Game.Result.valueOf(payload.getString("result") ?: badRequest("missing result"))
        return Json.Object("success" to true)
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getObjectPayload(request)
        val game = tournament.games[round][payload.getInt("id")] ?: badRequest("invalid game id")
        game.result = Game.Result.valueOf(payload.getString("result") ?: badRequest("missing result"))
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getObjectPayload(request)
        val game = tournament.games[round][payload.getInt("id")] ?: badRequest("invalid game id")
        tournament.games[round].remove(payload.getInt("id") ?: badRequest("invalid game id"))  ?: badRequest("invalid game id")
        return Json.Object("success" to true)
    }
}
