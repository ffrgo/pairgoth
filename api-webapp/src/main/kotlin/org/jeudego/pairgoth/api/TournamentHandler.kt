package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.api.ApiHandler.Companion.PAYLOAD_KEY
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.ext.OpenGotha
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.server.Event
import org.jeudego.pairgoth.server.Event.*
import org.w3c.dom.Element
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TournamentHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val accept = request.getHeader("Accept")
        return when (val id = getSelector(request)?.toIntOrNull()) {
            null -> Store.getTournaments().toJsonObject()
            else ->
                when {
                    ApiServlet.isJson(accept) -> Store.getTournament(id)?.toJson() ?: badRequest("no tournament with id #${id}")
                    ApiServlet.isXml(accept) -> {
                        val export = Store.getTournament(id)?.let { OpenGotha.export(it) } ?: badRequest("no tournament with id #${id}")
                        response.contentType = "application/xml; charset=UTF-8"
                        response.writer.write(export)
                        null // return null to indicate that we handled the response ourself
                    }
                    else -> badRequest("unhandled Accept header: $accept")
                }
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = when (val payload = request.getAttribute(PAYLOAD_KEY)) {
            is Json.Object -> Tournament.fromJson(getObjectPayload(request))
            is Element -> OpenGotha.import(payload)
            else -> badRequest("missing or invalid payload")
        }
        Store.addTournament(tournament)
        tournament.dispatchEvent(tournamentAdded, tournament.toJson())
        return Json.Object("success" to true, "id" to tournament.id)
    }

    override fun put(request: HttpServletRequest): Json {
        // BC TODO - some checks are needed here (cannot lower rounds number if games have been played in removed rounds, for instance)
        val tournament = getTournament(request)
        val payload = getObjectPayload(request)
        // disallow changing type
        if (payload.getString("type")?.let { it != tournament.type.name } == true) badRequest("tournament type cannot be changed")
        val updated = Tournament.fromJson(payload, tournament)
        // copy players, games, criteria (this copy should be provided by the Tournament class - CB TODO)
        updated.players.putAll(tournament.players)
        if (tournament is TeamTournament && updated is TeamTournament) {
            updated.teams.putAll(tournament.teams)
        }
        for (round in 1..tournament.lastRound()) updated.games(round).apply {
            clear()
            putAll(tournament.games(round))
        }
        Store.replaceTournament(updated)
        tournament.dispatchEvent(tournamentUpdated, tournament.toJson())
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        Store.deleteTournament(tournament)
        tournament.dispatchEvent(tournamentDeleted, Json.Object("id" to tournament.id))
        return Json.Object("success" to true)
    }
}
