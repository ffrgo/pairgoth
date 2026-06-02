package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.getStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Undo/history view of a tournament: the timeline of past actions (newest first), each restorable.
 *
 * Rows are *actions*; a row's category comes for free from the snapshot filename, while its nice
 * label is the `lastAction` of the state that action produced — which lives on the *newer* neighbour
 * snapshot (the current state for the most recent action). Restoring through a row reverts that
 * action and everything after it, i.e. loads the snapshot taken just before it.
 */
object HistoryHandler: PairgothApiHandler {

    private val TS_IN = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val TS_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private fun displayTime(ts: String) = try { LocalDateTime.parse(ts, TS_IN).format(TS_OUT) } catch (e: Exception) { ts }

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        val store = getStore(request)
        val archives = store.listHistory(tournament.id) // newest first; restore targets
        val before = request.getParameter("before")     // cursor = order key of the last shown row
        val count = request.getParameter("count")?.toIntOrNull()?.coerceIn(1, 100) ?: 10

        val start = if (before == null) 0
                    else archives.indexOfFirst { it.order < before }.let { if (it < 0) archives.size else it }
        val end = minOf(start + count, archives.size)

        val entries = (start until end).map { gi ->
            val snap = archives[gi]
            // label of the action that produced the next-newer state (current state for the top row)
            val label = if (gi == 0) tournament.lastAction else store.snapshotAction(tournament.id, archives[gi - 1].filename)
            Json.MutableObject(
                "restoreKey" to snap.filename,
                "category" to snap.slug, // null for legacy snapshots → UI shows "(earlier version)"
                "label" to label,
                "order" to snap.order,
                "time" to displayTime(snap.timestamp)
            )
        }.toJsonArray()
        return Json.MutableObject(
            "entries" to entries,
            "nextBefore" to if (end < archives.size) archives[end - 1].order else null
        )
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json {
        val tournament = getTournament(request)
        val snapshot = getObjectPayload(request).getString("snapshot") ?: badRequest("missing snapshot")
        getStore(request).restore(tournament.id, snapshot) ?: badRequest("cannot restore snapshot")
        return Json.Object("success" to true)
    }
}
