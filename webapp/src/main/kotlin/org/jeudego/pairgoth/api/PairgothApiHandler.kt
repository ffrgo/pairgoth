package org.jeudego.pairgoth.api

import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

interface PairgothApiHandler: ApiHandler {

    fun getTournament(request: HttpServletRequest): Tournament? = getSelector(request)?.toIntOrNull()?.let { Store.getTournament(it) }

}