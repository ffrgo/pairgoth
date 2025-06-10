package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import com.republicate.kson.toMutableJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.TeamTournament.Team
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.toID
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PairingHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round > tournament.lastRound() + 1) badRequest("invalid round: previous round has not been played")
        val playing = tournament.games(round).values.flatMap {
            listOf(it.black, it.white)
        }.toSet()
        val unpairables = tournament.pairables.values.filter { it.final && !it.canPlay(round) }.sortedByDescending { it.rating }.map { it.id }.toJsonArray()
        val pairables = tournament.pairables.values.filter { it.final && it.canPlay(round) && !playing.contains(it.id) }.sortedByDescending { it.rating }.map { it.id }.toJsonArray()
        val games = tournament.games(round).values.sortedBy {
            if (it.table == 0) Int.MAX_VALUE else it.table
        }
        val ret = Json.MutableObject(
            "games" to games.map { it.toJson() }.toCollection(Json.MutableArray()),
            "pairables" to pairables,
            "unpairables" to unpairables
        )
        if (tournament is TeamTournament) {
            ret["individualGames"] = tournament.individualGames(round).values.map { it.toJson() }.toJsonArray()
        }
        return ret
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round > tournament.lastRound() + 1) badRequest("invalid round: previous round has not been played")
        val payload = getArrayPayload(request)
        if (payload.isEmpty()) badRequest("nobody to pair")
        // CB TODO - change convention to empty array for all players
        val allPlayers = payload.size == 1 && payload[0] == "all"
        //if (!allPlayers && tournament.pairing.type == PairingType.SWISS) badRequest("Swiss pairing requires all pairable players")
        val playing = (tournament.games(round).values).flatMap {
            listOf(it.black, it.white)
        }.toSet()
        val pairables =
            if (allPlayers)
                tournament.pairables.values.filter { it.final && !it.skip.contains(round) && !playing.contains(it.id) }
            else payload.map {
                // CB - because of the '["all"]' map, conversion to int lands here... Better API syntax for 'all players'?
                if (it is Number) it.toID() else badRequest("invalid pairable id: #$it")
            }.map { id ->
                tournament.pairables[id]?.also {
                    if (!it.final) badRequest("pairable #$id registration status is not final")
                    if (it.skip.contains(round)) badRequest("pairable #$id does not play round $round")
                    if (playing.contains(it.id)) badRequest("pairable #$id already plays round $round")
                } ?: badRequest("invalid pairable id: #$id")
            }
        val games = tournament.pair(round, pairables)

        val ret = games.map { it.toJson() }.toJsonArray()
        tournament.dispatchEvent(GamesAdded, request, Json.Object("round" to round, "games" to ret))
        return ret
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        // only allow last round (if players have not been paired in the last round, it *may* be possible to be more laxist...)
        // TODO - check in next line commented out: following founds can exist, but be empty...
        // if (round != tournament.lastRound()) badRequest("cannot edit pairings in other rounds but the last")
        val payload = getObjectPayload(request)
        if (payload.containsKey("id")) {
            val gameId = payload.getInt("id") ?: badRequest("invalid game id")
            val game = tournament.games(round)[gameId] ?: badRequest("invalid game id")
            val playing = (tournament.games(round).values).filter { it.id != gameId }.flatMap {
                listOf(it.black, it.white)
            }.toSet()
            if (game.result != Game.Result.UNKNOWN && (
                    game.black != payload.getInt("b") ||
                    game.white != payload.getInt("w") ||
                    game.handicap != payload.getInt("h")
            )) badRequest("Game already has a result")
            game.black = payload.getID("b") ?: badRequest("missing black player id")
            game.white = payload.getID("w") ?: badRequest("missing white player id")

            tournament.recomputeDUDD(round, game.id)
            val previousTable = game.table;
            // temporary
            //payload.getInt("dudd")?.let { game.drawnUpDown = it }
            val black = tournament.pairables[game.black] ?: badRequest("invalid black player id")
            val white = tournament.pairables[game.black] ?: badRequest("invalid white player id")
            if (!black.final) badRequest("black registration status is not final")
            if (!white.final) badRequest("white registration status is not final")
            if (black.skip.contains(round)) badRequest("black is not playing this round")
            if (white.skip.contains(round)) badRequest("white is not playing this round")
            if (playing.contains(black.id)) badRequest("black is already in another game")
            if (playing.contains(white.id)) badRequest("white is already in another game")
            if (payload.containsKey("h")) game.handicap = payload.getString("h")?.toIntOrNull() ?:  badRequest("invalid handicap")
            if (payload.containsKey("t")) {
                game.table = payload.getString("t")?.toIntOrNull() ?:  badRequest("invalid table number")
                game.forcedTable = true
            }
            tournament.dispatchEvent(GameUpdated, request, Json.Object("round" to round, "game" to game.toJson()))
            if (game.table != previousTable) {
                val tableWasOccupied = ( tournament.games(round).values.find { g -> g != game && g.table == game.table } != null )
                if (tableWasOccupied) {
                    // some renumbering is necessary
                    renumberTables(request, tournament, round, game)
                }
            }
            return Json.Object("success" to true)
        } else {
            // without id, it's a table renumbering
            if (payload.containsKey("excludeTables")) {
                val tablesExclusion = payload.getString("excludeTables") ?: badRequest("missing 'excludeTables'")
                TournamentHandler.validateTablesExclusion(tablesExclusion)
                while (tournament.tablesExclusion.size < round) tournament.tablesExclusion.add("")
                tournament.tablesExclusion[round - 1] = tablesExclusion
                tournament.dispatchEvent(TournamentUpdated, request, tournament.toJson())
            }

            renumberTables(request, tournament, round)

            return Json.Object("success" to true)
        }
    }

    private fun renumberTables(request: HttpServletRequest, tournament: Tournament<*>, round: Int, pivot: Game? = null) {
        val sortedPairables = tournament.getSortedPairables(round)
        val sortedMap = sortedPairables.associateBy {
            it.getID()!!
        }
        val changed = tournament.renumberTables(round, pivot) { gm ->
            val whitePosition = sortedMap[gm.white]?.getInt("num") ?: Int.MIN_VALUE
            val blackPosition = sortedMap[gm.black]?.getInt("num") ?: Int.MIN_VALUE
            (whitePosition + blackPosition)
        }
        if (changed) {
            val games = tournament.games(round).values.sortedBy {
                if (it.table == 0) Int.MAX_VALUE else it.table
            }
            tournament.dispatchEvent(
                TablesRenumbered, request,
                Json.Object(
                    "round" to round,
                    "games" to games.map { it.toJson() }.toCollection(Json.MutableArray())
                )
            )
        }

    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        val payload = getArrayPayload(request)
        val allPlayers = payload.size == 1 && payload[0] == "all"
        if (allPlayers) {
            // TODO - just remove this, it is never used ; and no check is done on whether the players are playing...
            tournament.unpair(round)
        } else {
            payload.forEach {
                val id = (it as Number).toID()
                val game = tournament.games(round)[id] ?: throw Error("invalid game id")
                if (game.result != Game.Result.UNKNOWN && game.black != 0 && game.white != 0) {
                    ApiHandler.logger.error("cannot unpair game id ${game.id}: it has a result")
                    // we'll only skip it
                    // throw Error("cannot unpair ")
                } else {
                    tournament.unpair(round, id)
                }
            }
        }
        tournament.dispatchEvent(GamesDeleted, request, Json.Object("round" to round, "games" to payload))
        return Json.Object("success" to true)
    }
}
