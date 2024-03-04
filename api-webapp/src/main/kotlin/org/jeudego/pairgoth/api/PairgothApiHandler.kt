package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.server.Event
import org.jeudego.pairgoth.store.getStore
import javax.servlet.http.HttpServletRequest

interface PairgothApiHandler: ApiHandler {

    fun getTournament(request: HttpServletRequest): Tournament<*> {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid tournament id")
        return getStore(request).getTournament(tournamentId) ?: ApiHandler.badRequest("unknown tournament id")
    }

    fun Tournament<*>.dispatchEvent(event: Event, request: HttpServletRequest, data: Json? = null) {
        Event.dispatch(event, Json.Object("tournament" to id, "data" to data))
        // when storage is not in memory, the tournament has to be persisted
        if (event != Event.TournamentAdded && event != Event.TournamentDeleted)
            getStore(request).replaceTournament(this)
    }
}
