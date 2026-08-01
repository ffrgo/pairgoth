package org.jeudego.pairgoth.server

import info.macias.sse.events.MessageEvent
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
        private fun <T> buildEvent(event: Event, data: T) = MessageEvent.Builder()
            .setId("${nextMessageId.incrementAndGet()}".padStart(10, '0'))
            .setEvent(event.name)
            .setData(data.toString())
            .build()
        internal fun <T> dispatch(event: Event, data: T) {
            sse.broadcast(buildEvent(event, data))
        }
    }
}
