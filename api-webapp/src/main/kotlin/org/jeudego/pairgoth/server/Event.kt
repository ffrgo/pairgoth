package org.jeudego.pairgoth.server

import com.republicate.kson.Json
import info.macias.sse.events.MessageEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

// `slug` is a closed-vocabulary, ASCII, <=16-char category embedded in history snapshot filenames
// (no user input → portable across filesystems, faithfully readable via `ls`). Used by the undo view.
enum class Event(val slug: String) {
    TournamentAdded("add-tourney"),
    TournamentUpdated("edit-tourney"),
    TournamentDeleted("del-tourney"),
    PlayerAdded("add-player"),
    PlayerUpdated("edit-player"),
    PlayerDeleted("del-player"),
    PlayersImported("import-players"),
    RatingsRefreshed("refresh-ratings"),
    MMGroupsUpdated("mm-groups"),
    TeamAdded("add-team"),
    TeamUpdated("edit-team"),
    TeamDeleted("del-team"),
    GamesAdded("pair"),
    GamesDeleted("unpair"),
    GameUpdated("edit-game"),
    ResultUpdated("enter-result"),
    ResultsCleared("clear-results"),
    TablesRenumbered("renumber"),
    StandingsUpdated("set-criteria"),
    PairingParamsUpdated("pairing-params"),
    PairingsPublished("publish")
    ;

    companion object {
        // seeded with the boot time so id ranges never overlap across restarts: a cursor from a
        // previous boot must fall outside the new history (jeasse replayHasGap), never inside it
        private val nextMessageId = AtomicLong(System.currentTimeMillis())
        private val sse: SSEServlet by lazy { SSEServlet.getInstance() }

        // this boot's identity: SSE clients receive it in the volatile `hello`, polling clients in
        // every response — a change tells them their cursor belongs to a dead history
        val bootId: String = UUID.randomUUID().toString()

        // bounded event log for the polling transport (the SSE replay history lives inside jeasse):
        // one writer (dispatch), read by SSEServlet's JSON branch
        private const val LOG_SIZE = 500
        private val log = ArrayDeque<Json.Object>()

        private fun buildEvent(id: Long, event: Event, data: Json) = MessageEvent.Builder()
            .setId("$id".padStart(10, '0'))
            .setEvent(event.name)
            .setData(data.toString())
            .build()

        internal fun dispatch(event: Event, data: Json) {
            val id = nextMessageId.incrementAndGet()
            synchronized(log) {
                log.addLast(Json.Object("id" to id, "name" to event.name, "data" to data))
                while (log.size > LOG_SIZE) log.removeFirst()
            }
            sse.broadcast(buildEvent(id, event, data))
        }

        /** Polling view of the log: events with id > since. `gap` when pruning ate part of the
         *  asked range; a null/absent `since` just returns the current cursor to start from. */
        internal fun eventsSince(since: Long?): Json.Object {
            val events = Json.MutableArray()
            var gap = false
            var cursor = since ?: 0L
            synchronized(log) {
                if (since == null) {
                    cursor = log.lastOrNull()?.getLong("id") ?: nextMessageId.get()
                } else {
                    val first = log.firstOrNull()?.getLong("id")
                    if (first != null && since < first - 1) gap = true
                    for (entry in log) {
                        val id = entry.getLong("id")!!
                        if (id > since) {
                            events.add(entry)
                            cursor = id
                        }
                    }
                }
            }
            return Json.Object("boot" to bootId, "gap" to gap, "cursor" to cursor, "events" to events)
        }
    }
}
