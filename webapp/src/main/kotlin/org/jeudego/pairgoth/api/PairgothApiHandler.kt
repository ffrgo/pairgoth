package org.jeudego.pairgoth.api

import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

interface PairgothApiHandler: ApiHandler {

    fun getTournament(request: HttpServletRequest): Tournament<*> {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid tournament id")
        return Store.getTournament(tournamentId) ?: ApiHandler.badRequest("unknown tournament id")
    }


}