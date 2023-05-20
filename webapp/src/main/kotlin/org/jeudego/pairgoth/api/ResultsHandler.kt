package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.web.Event
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ResultsHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val games = tournament.games.getOrNull(round)?.values ?: emptyList()
        return games.map { it.toJson() }.toJsonArray()
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getObjectPayload(request)
        val game = tournament.games[round][payload.getInt("id")] ?: badRequest("invalid game id")
        game.result = Game.Result.valueOf(payload.getString("result") ?: badRequest("missing result"))
        Event.dispatch(Event.resultUpdated, Json.Object("tournament" to tournament.id, "round" to round, "data" to game))
        return Json.Object("success" to true)
    }
}
