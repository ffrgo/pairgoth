package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.api.ApiHandler.Companion.PAYLOAD_KEY
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.ext.OpenGotha
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toFullJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.server.Event.*
import org.jeudego.pairgoth.store.getStore
import org.w3c.dom.Element
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TournamentHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val accept = request.getHeader("Accept")
        return when (val id = getSelector(request)?.toIntOrNull()) {
            null -> getStore(request).getTournaments().toJsonObject()
            else ->
                when {
                    ApiServlet.isJson(accept) -> {
                        getStore(request).getTournament(id)?.let { tour ->
                            if (accept == "application/pairgoth") {
                                tour.toFullJson()
                            } else {
                                tour.toJson().also { json ->
                                    // additional attributes for the webapp
                                    json["stats"] = tour.stats()
                                    json["teamSize"] = tour.type.playersNumber
                                }
                            }
                        } ?: badRequest("no tournament with id #${id}")
                    }
                    ApiServlet.isXml(accept) -> {
                        val export = getStore(request).getTournament(id)?.let { OpenGotha.export(it) } ?: badRequest("no tournament with id #${id}")
                        response.contentType = "application/xml; charset=UTF-8"
                        response.writer.write(export)
                        null // return null to indicate that we handled the response ourselves
                    }
                    else -> badRequest("unhandled Accept header: $accept")
                }
        }
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = when (val payload = request.getAttribute(PAYLOAD_KEY)) {
            is Json.Object -> Tournament.fromJson(getObjectPayload(request))
            is Element -> OpenGotha.import(payload)
            else -> badRequest("missing or invalid payload")
        }
        getStore(request).addTournament(tournament)
        tournament.dispatchEvent(TournamentAdded, request, tournament.toJson())
        return Json.Object("success" to true, "id" to tournament.id)
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json {
        // CB TODO - some checks are needed here (cannot lower rounds number if games have been played in removed rounds, for instance)
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
        updated.dispatchEvent(TournamentUpdated, request, updated.toJson())
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        getStore(request).deleteTournament(tournament)
        tournament.dispatchEvent(TournamentDeleted, request, Json.Object("id" to tournament.id))
        return Json.Object("success" to true)
    }
}
