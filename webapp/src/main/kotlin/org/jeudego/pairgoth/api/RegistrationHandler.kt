package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object RegistrationHandler: ApiHandler {

    private fun getTournament(request: HttpServletRequest): Tournament {
        val tournamentId = getSelector(request)?.toIntOrNull() ?: badRequest("invalid tournament id")
        return Store.getTournament(tournamentId) ?: badRequest("unknown tournament id")
    }

    override fun get(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        return when (val pairableId = getSubSelector(request)?.toIntOrNull()) {
            null -> when (val round = request.getParameter("round")?.toIntOrNull()) {
                null -> Json.Array(tournament.pairables.map {
                    Json.Object(
                        "id" to it.key,
                        "skip" to Json.Array(it.value)
                    )
                })
                else -> Json.Array(tournament.pairables.filter { !it.value.contains(round) }.keys)
            }
            else -> Json.Array(tournament.pairables[pairableId])
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val payload = getObjectPayload(request)
        val pairableId = payload.getInt("id") ?: badRequest("missing player id")
        val skip = ( payload.getArray("skip") ?: Json.Array() ).map { Json.TypeUtils.toInt(it) ?: badRequest("invalid round number") }
        if (tournament.pairables.contains(pairableId)) badRequest("already registered player: $pairableId")
        /* CB TODO - update action for SSE channel */
        tournament.pairables[pairableId] = skip.toMutableSet()
        return Json.Object("success" to true)
    }

}
