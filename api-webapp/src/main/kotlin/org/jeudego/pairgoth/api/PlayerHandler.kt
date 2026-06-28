package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import com.republicate.kson.toMutableJsonObject
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.DatabaseId
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.server.ApiException
import org.jeudego.pairgoth.server.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PlayerHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> tournament.players.values.map { it.toJson() }.toJsonArray()
            else -> tournament.players[pid]?.toJson() ?: badRequest("no player with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val payload = getPayload(request)
        // a json array is a roster upsert (event push, or the operator's re-sync / refresh-ratings)
        if (payload.isArray) return bulkUpsert(tournament, payload.asArray(), request)
        val obj = payload.asObject()
        val player = Player.fromJson(obj)
        tournament.findPlayerByExternalIds(player.externalIds)?.let { existing ->
            badRequest(
                if (existing.final) "player already registered"
                else "player already registered (preliminary — toggle 'show preliminary' to find them)"
            )
        }
        tournament.players[player.id] = player
        tournament.dispatchEvent(PlayerAdded, request, player.toJson())
        return Json.Object("success" to true, "id" to player.id)
    }

    /**
     * Roster upsert in a single shot: each entry is matched by `id`, else by external id, else created;
     * partial payloads merge onto the existing player. Applies everything in memory and emits ONE
     * [PlayersImported] event (one history snapshot, not one per player) — the result is a journal,
     * which is why this lives here and not in a per-player browser loop.
     */
    private fun bulkUpsert(tournament: Tournament<*>, roster: Json.Array, request: HttpServletRequest): Json {
        // Per-section journals (names / {player, changes} / {player, reason}) rather than bare counts,
        // so the operator report can detail who changed and how, not just how many.
        val added = Json.MutableArray()
        val updated = Json.MutableArray()
        val unchanged = Json.MutableArray()
        val failed = Json.MutableArray()
        roster.forEach { entry ->
            if (entry !is Json.Object) { failed.add(Json.Object("reason" to "not a json object")); return@forEach }
            val p = entry
            val label = listOfNotNull(p.getString("name"), p.getString("firstname"))
                .joinToString(" ").ifBlank { "#${p.getInt("id") ?: "?"}" }
            try {
                val existing = p.getInt("id")?.let { tournament.players[it] }
                    ?: tournament.findPlayerByExternalIds(externalIdsOf(p))
                if (existing == null) {
                    val player = Player.fromJson(p)
                    tournament.players[player.id] = player
                    added.add(label)
                } else {
                    val merged = Player.fromJson(p, existing)
                    participationConflict(tournament, existing, merged)?.let { badRequest(it) }
                    val before = existing.toJson(); val after = merged.toJson()
                    if (before == after) unchanged.add(label)
                    else {
                        tournament.players[existing.id] = merged
                        updated.add(Json.Object("player" to label, "changes" to diffLabel(before, after)))
                    }
                }
            } catch (e: ApiException) {
                failed.add(Json.Object("player" to label, "reason" to (e.message ?: "error")))
            }
        }
        // one event / one history snapshot — and none at all when nothing actually changed. The event
        // (hence the undo-list label and history slug) reflects the *intent* the caller declares via
        // ?reason=, so a Mac Mahon group edit or a ratings refresh isn't mislabelled as a roster import.
        val event = when (request.getParameter("reason")) {
            "mms" -> MMGroupsUpdated
            "ratings" -> RatingsRefreshed
            else -> PlayersImported
        }
        if (added.isNotEmpty() || updated.isNotEmpty())
            tournament.dispatchEvent(event, request, Json.Object("added" to added.size, "updated" to updated.size))
        return Json.Object("success" to true,
            "added" to added, "updated" to updated, "unchanged" to unchanged, "failed" to failed)
    }

    /** Compact human diff of two player snapshots: "field old→new, …" over the keys that changed (id aside). */
    private fun diffLabel(before: Json.Object, after: Json.Object): String {
        val keys = LinkedHashSet(before.keys).apply { addAll(after.keys); remove("id") }
        fun show(v: Any?) = v?.toString() ?: "∅"
        return keys.filter { before[it] != after[it] }
            .joinToString(", ") { "$it ${show(before[it])}→${show(after[it])}" }
    }

    private fun externalIdsOf(p: Json.Object): Map<DatabaseId, String> =
        DatabaseId.values().mapNotNull { db -> p.getString(db.key)?.takeIf { it.isNotBlank() }?.let { db to it } }.toMap()

    /** The paired-player coherence guards; returns a human reason if the change is illegal, else null. */
    private fun participationConflict(tournament: Tournament<*>, player: Player, updated: Player): String? {
        if (player.final && !updated.final && tournament.pairedPlayers().contains(updated.id))
            return "player is playing"
        val leavingRounds = updated.skip.toSet().minus(player.skip.toSet())
        leavingRounds.forEach { round ->
            if (round <= tournament.lastRound() && tournament.pairedPlayers(round).contains(player.id))
                return "player is playing in round #$round"
        }
        if (tournament is TeamTournament) {
            // participations cannot be changed in an already paired team
            val joiningRounds = player.skip.toSet().minus(updated.skip.toSet())
            (leavingRounds + joiningRounds).forEach { round ->
                if (round <= tournament.lastRound()) {
                    val team = tournament.getPlayerTeam(player.id)
                    if (team != null && tournament.pairedTeams().contains(team.id))
                        return "team #${team.id} active players cannot change for round $round"
                }
            }
        }
        return null
    }

    override fun put(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val player = tournament.players[id] ?: badRequest("invalid player id")
        val payload = getObjectPayload(request)
        val updated = Player.fromJson(payload, player)
        participationConflict(tournament, player, updated)?.let { badRequest(it) }
        // changes mask for the collaborative SSE client: bit1 identity, bit2 reg-status (final),
        // bit3 participation (skip). Observers patch on 2/3-only changes, reload on identity.
        fun identity(p: Player) = p.toJson().toMutableJsonObject().also { it.remove("final"); it.remove("skip") }
        var changes = 0
        if (identity(player) != identity(updated)) changes = changes or 1
        if (player.final != updated.final) changes = changes or 2
        if (player.skip != updated.skip) changes = changes or 4
        tournament.players[id] = updated
        tournament.dispatchEvent(PlayerUpdated, request, updated.toJson().toMutableJsonObject().also { it["changes"] = changes })
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        // check coherence
        val player = tournament.players[id] ?: badRequest("invalid player id")
        if (player.final && tournament.pairedPlayers().contains(id)) {
            badRequest("player is playing")
        }
        // a team member cannot be deleted directly: it would leave a dangling id in the team,
        // which silently shrinks the team and makes the tournament unloadable on the next reload
        if (tournament is TeamTournament) tournament.getPlayerTeam(id)?.let {
            badRequest("player belongs to team #${it.id}")
        }
        tournament.players.remove(id) ?: badRequest("invalid player id")
        tournament.dispatchEvent(PlayerDeleted, request, Json.Object("id" to id))
        return Json.Object("success" to true)
    }
}
