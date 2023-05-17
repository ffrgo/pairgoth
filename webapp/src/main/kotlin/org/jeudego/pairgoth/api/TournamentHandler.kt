package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object TournamentHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest): Json {
        return when (val id = getSelector(request)?.toIntOrNull()) {
            null -> Json.Array(Store.getTournamentsIDs())
            else -> Store.getTournament(id)?.toJson() ?: badRequest("no tournament with id #${id}")
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val payload = getObjectPayload(request)

        // tournament parsing
        val tournament = Tournament.fromJson(payload)

        Store.addTournament(tournament)
        return Json.Object("success" to true, "id" to tournament.id)
    }

    override fun put(request: HttpServletRequest): Json {
        // BC TODO - some checks are needed here (cannot lower rounds number if games have been played in removed rounds, for instance)
        val tournament = getTournament(request) ?: badRequest("missing or invalid tournament id")
        val payload = getObjectPayload(request)
        // disallow changing type
        if (payload.getString("type")?.let { it != tournament.type.name } == true) badRequest("tournament type cannot be changed")
        val updated = Tournament.fromJson(payload, tournament)
        updated.pairables.putAll(tournament.pairables)
        updated.games.addAll(tournament.games)
        updated.criteria.addAll(tournament.criteria)
        Store.replaceTournament(updated)
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request) ?: badRequest("missing or invalid tournament id")
        Store.deleteTournament(tournament)
        return Json.Object("success" to true)
    }
}
