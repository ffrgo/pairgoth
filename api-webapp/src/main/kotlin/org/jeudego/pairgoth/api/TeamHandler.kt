package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.server.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TeamHandler: PairgothApiHandler {

    // a player belongs to at most one team; the UI only implies it, stale views don't
    private fun checkPlayersFree(tournament: TeamTournament, team: TeamTournament.Team, self: Int? = null) {
        team.playerIds.forEach { id ->
            val owner = tournament.getPlayerTeam(id)
            if (owner != null && owner.id != self) {
                badRequest("player ${tournament.players[id]?.fullName() ?: "#$id"} is already in team \"${owner.name}\"")
            }
        }
    }

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> tournament.teams.values.map { it.toDetailedJson() }.toJsonArray()
            else -> tournament.teams[pid]?.toDetailedJson() ?: badRequest("no team with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val payload = getObjectPayload(request)
        val team = tournament.teamFromJson(payload)
        checkPlayersFree(tournament, team)
        tournament.teams[team.id] = team
        tournament.dispatchEvent(TeamAdded, request, team.toJson())
        return Json.Object("success" to true, "id" to team.id)
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val team = tournament.teams[id] ?: badRequest("invalid team id")
        val payload = getObjectPayload(request)
        val updated = tournament.teamFromJson(payload, team)
        checkPlayersFree(tournament, updated, team.id)
        // late arrivals sit out the rounds the team already played: pairing stays untouched
        // and the round check below still sees the same active players for those rounds
        (updated.playerIds - team.playerIds).forEach { id ->
            val newcomer = tournament.players[id]!!
            for (round in 1..tournament.lastRound()) {
                if (tournament.pairedTeams(round).contains(team.id)) newcomer.skip.add(round)
            }
        }
        for (round in 1..tournament.lastRound()) {
            if (tournament.pairedTeams(round).contains(team.id) && !updated.canPlay(round)) {
                badRequest("team is playing round #$round, number of pairable players cannot change for this round")
            }
        }
        tournament.teams[updated.id] = updated
        tournament.dispatchEvent(TeamUpdated, request, team.toJson())
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid team selector")
        if (tournament.pairedTeams().contains(id)) {
            badRequest("team is playing");
        }
        tournament.teams.remove(id) ?: badRequest("invalid team id")
        tournament.dispatchEvent(TeamDeleted, request, Json.Object("id" to id))
        return Json.Object("success" to true)
    }
}
