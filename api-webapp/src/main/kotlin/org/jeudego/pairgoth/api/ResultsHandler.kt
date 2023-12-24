package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.Event
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ResultsHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val games = tournament.games(round).values
        return games.map { it.toJson() }.toJsonArray()
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round != tournament.lastRound()) badRequest("cannot enter results in other rounds but the last")
        val payload = getObjectPayload(request)
        val game = tournament.games(round)[payload.getInt("id")] ?: badRequest("invalid game id")
        game.result = Game.Result.fromSymbol(payload.getChar("result") ?: badRequest("missing result"))
        tournament.dispatchEvent(Event.resultUpdated, Json.Object("round" to round, "data" to game))
        return Json.Object("success" to true)
    }
}
