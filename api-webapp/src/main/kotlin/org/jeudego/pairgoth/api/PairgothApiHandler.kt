package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.server.Event
import javax.servlet.http.HttpServletRequest

interface PairgothApiHandler: ApiHandler {

    fun getTournament(request: HttpServletRequest): Tournament<*> {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("invalid tournament id")
        return Store.getTournament(tournamentId) ?: ApiHandler.badRequest("unknown tournament id")
    }

    fun Tournament<*>.dispatchEvent(event: Event, data: Json? = null) {
        Event.dispatch(event, Json.Object("tournament" to id, "data" to data))
        // when storage is not in memory, the tournament has to be persisted
        if (event != Event.tournamentAdded && event != Event.tournamentDeleted)
            Store.replaceTournament(this)
    }

}
