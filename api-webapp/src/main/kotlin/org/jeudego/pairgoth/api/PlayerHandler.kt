package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import com.republicate.kson.toMutableJsonObject
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.DatabaseId
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.server.ApiException
import org.jeudego.pairgoth.server.Event.*
import org.jeudego.pairgoth.server.WebappManager
import org.jeudego.pairgoth.util.proToRating
import org.jeudego.pairgoth.util.rankToRating
import org.jeudego.pairgoth.util.ratingToPro
import org.jeudego.pairgoth.util.ratingToRank
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
        // A roster import carries the full source roster, so any pre-existing player it leaves
        // untouched has been removed on the source side. Never deleted here, but unregistered
        // from the rounds still open to them (see the `missing` journal below).
        val preExisting = tournament.players.values.toList()
        val touched = mutableSetOf<ID>()
        val rankAuthoritative = WebappManager.properties.getProperty("ratings.rank_authoritative")?.toBoolean() ?: false
        roster.forEach { entry ->
            if (entry !is Json.Object) { failed.add(Json.Object("reason" to "not a json object")); return@forEach }
            val p = if (rankAuthoritative) enforceRankAuthority(entry) else entry
            val label = listOfNotNull(p.getString("name"), p.getString("firstname"))
                .joinToString(" ").ifBlank { "#${p.getInt("id") ?: "?"}" }
            try {
                val existing = p.getInt("id")?.let { tournament.players[it] }
                    ?: tournament.findPlayerByExternalIds(externalIdsOf(p))
                // touched at resolution, not on success: a blocked update is still present upstream
                existing?.let { touched.add(it.id) }
                if (existing == null) {
                    val player = Player.fromJson(p)
                    tournament.players[player.id] = player
                    added.add(label)
                } else {
                    // Level lock: rating/rank/pro of a locked player survive every bulk import
                    // (the refresh loops know nothing of exceptions). An explicit locked:false
                    // unlocks and applies its values in one shot; an absent flag never unlocks.
                    val source = if (existing.locked && p.getBoolean("locked") != false)
                        Json.MutableObject(p).also { it.remove("rating"); it.remove("rank"); it.remove("pro") }
                    else p
                    val merged = Player.fromJson(source, existing)
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
        // stamp even when nothing changed (no event): "synced, all up to date" must silence the
        // pair-without-sync confirm too; the cached instance carries it, the file catches up later
        if (event == PlayersImported) tournament.lastSync = System.currentTimeMillis()
        // partial payloads (?reason= — ratings refresh, mm group edits) can't tell removed from omitted.
        // A missing player is kept but unregistered from every round still open to them — the round
        // filter mirrors participationConflict, so already-paired rounds (and paired-team rounds)
        // stay untouched; the journal entry carries the change when there is one.
        val missing = Json.MutableArray()
        var unregistered = 0
        if (event == PlayersImported) preExisting.filter { it.id !in touched }.forEach { player ->
            val remaining = (1..tournament.rounds).filter { round ->
                round !in player.skip &&
                    (round > tournament.lastRound() || player.id !in tournament.pairedPlayers(round)) &&
                    (tournament !is TeamTournament || round > tournament.lastRound() ||
                        tournament.getPlayerTeam(player.id).let { it == null || it.id !in tournament.pairedTeams() })
            }
            val entry = Json.MutableObject("player" to "${player.name} ${player.firstname}")
            if (remaining.isNotEmpty()) {
                player.skip.addAll(remaining)
                unregistered++
                entry["changes"] = "unregistered from round${if (remaining.size > 1) "s" else ""} ${remaining.joinToString(", ")}"
            }
            missing.add(entry)
        }
        if (added.isNotEmpty() || updated.isNotEmpty() || unregistered > 0)
            tournament.dispatchEvent(event, request, Json.Object(
                "added" to added.size, "updated" to updated.size, "unregistered" to unregistered))
        return Json.Object("success" to true,
            "added" to added, "updated" to updated, "unchanged" to unchanged, "failed" to failed,
            "missing" to missing)
    }

    /**
     * `ratings.rank_authoritative`: the imported rank is the level truth. When an entry carries
     * both rank and rating and they disagree (rating out of the rank's band), the rating is
     * snapped to the rank's nominal value — imported players always land chained in the UI.
     * In-band ratings keep their finer-grained value; partial entries missing either field
     * pass through untouched (locked players are stripped later and stay immune regardless).
     */
    private fun enforceRankAuthority(p: Json.Object): Json.Object {
        val rank = p.getInt("rank") ?: return p
        val rating = p.getInt("rating") ?: return p
        val pro = p.getInt("pro") ?: 0
        val linked = if (pro > 0) ratingToPro(rating) == pro else ratingToRank(rating) == rank
        return if (linked) p
        else Json.MutableObject(p).also { it["rating"] = if (pro > 0) proToRating(pro) else rankToRating(rank) }
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
