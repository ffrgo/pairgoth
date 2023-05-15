package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object PairingHandler: ApiHandler {

    private fun getTournament(request: HttpServletRequest): Tournament {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid tournament id")
        return Store.getTournament(tournamentId) ?: ApiHandler.badRequest("unknown tournament id")
    }

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val round = request.getParameter("round")?.toIntOrNull() ?: badRequest("invalid round number")
        return Json.Object();
    }
}