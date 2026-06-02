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
        // when storage is not in memory, the tournament has to be persisted; the event's slug names
        // the history snapshot (category), and lastAction is the human label, for the undo view
        if (event != Event.TournamentAdded && event != Event.TournamentDeleted) {
            lastAction = actionLabel(event, data)
            getStore(request).replaceTournament(this, event.slug)
        }
    }

    /** Best-effort human label for the action, used by the undo/history view; falls back to the slug. */
    private fun actionLabel(event: Event, data: Json?): String {
        val obj = data as? Json.Object
        val ofRound = obj?.getInt("round")?.let { " (round $it)" } ?: ""
        fun playerName() = listOfNotNull(obj?.getString("name"), obj?.getString("firstname"))
            .joinToString(" ").ifBlank { "" }.let { if (it.isBlank()) "" else " $it" }
        return when (event) {
            Event.PlayerAdded -> "Add player${playerName()}"
            Event.PlayerUpdated -> "Edit player${playerName()}"
            Event.PlayerDeleted -> "Remove player"
            Event.TeamAdded -> "Add team"
            Event.TeamUpdated -> "Edit team"
            Event.TeamDeleted -> "Remove team"
            Event.GamesAdded -> "Pair$ofRound"
            Event.GamesDeleted -> "Unpair$ofRound"
            Event.GameUpdated -> "Edit pairing$ofRound"
            Event.ResultUpdated -> "Enter result$ofRound"
            Event.ResultsCleared -> "Clear results$ofRound"
            Event.TablesRenumbered -> "Renumber tables$ofRound"
            Event.TournamentUpdated -> "Edit tournament settings"
            else -> event.slug.replace('-', ' ')
        }
    }
}
