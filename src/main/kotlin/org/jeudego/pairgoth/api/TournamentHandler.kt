package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.CanadianByoyomi
import org.jeudego.pairgoth.model.FisherTime
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Rules
import org.jeudego.pairgoth.model.StandardByoyomi
import org.jeudego.pairgoth.model.SuddenDeath
import org.jeudego.pairgoth.model.TimeSystem
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.TournamentType
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

class TournamentHandler(): ApiHandler {

    override fun post(request: HttpServletRequest): Json {
        val json = getPayload(request)
        if (!json.isObject) badRequest("expecting a json object")
        val payload = json.asObject()

        // tournament parsing
        val tournament = Tournament.fromJson(payload)

        Store.addTournament(tournament)
        return Json.Object("success" to true, "id" to tournament.id)
    }

    override fun get(request: HttpServletRequest): Json {
        return when (val id = getSelector(request)?.toIntOrNull()) {
            null -> Json.Array(Store.getTournamentsIDs())
            else -> Store.getTournament(id)?.toJson() ?: badRequest("no tournament with id #${id}")
        }
    }

    override fun put(request: HttpServletRequest): Json {
        val id = getSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid tournament selector")
        TODO()
    }
}
