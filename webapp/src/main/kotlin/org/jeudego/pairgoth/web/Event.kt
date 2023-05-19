package org.jeudego.pairgoth.web

import info.macias.sse.events.MessageEvent
import java.util.concurrent.atomic.AtomicLong

enum class Event {
    tournamentAdded,
    tournamentUpdated,
    tournamentDeleted,
    playerAdded,
    playerUpdated,
    playerDeleted,
    gamesAdded,
    gamesDeleted,
    resultUpdated,
    ;

    companion object {
        private val nextMessageId = AtomicLong(0)
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
