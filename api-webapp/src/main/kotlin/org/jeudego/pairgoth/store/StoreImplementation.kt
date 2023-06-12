package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger

internal val _nextTournamentId = AtomicInteger()
internal val _nextPlayerId = AtomicInteger()
internal val _nextGameId = AtomicInteger()

interface StoreImplementation {

    val nextTournamentId get() = _nextTournamentId.incrementAndGet()
    val nextPlayerId get() = _nextPlayerId.incrementAndGet()
    val nextGameId get() = _nextGameId.incrementAndGet()

    fun getTournaments(): Map<ID, String>
    fun addTournament(tournament: Tournament<*>)
    fun getTournament(id: ID): Tournament<*>?
    fun replaceTournament(tournament: Tournament<*>)
    fun deleteTournament(tournament: Tournament<*>)
}
