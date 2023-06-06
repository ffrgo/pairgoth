package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger

private val _nextTournamentId = AtomicInteger()
private val _nextPlayerId = AtomicInteger()
private val _nextGameId = AtomicInteger()

interface StoreImplementation {

    val nextTournamentId get() = _nextTournamentId.incrementAndGet()
    val nextPlayerId get() = _nextPlayerId.incrementAndGet()
    val nextGameId get() = _nextGameId.incrementAndGet()

    fun getTournamentsIDs(): Set<ID>
    fun addTournament(tournament: Tournament<*>)
    fun getTournament(id: ID): Tournament<*>?
    fun replaceTournament(tournament: Tournament<*>)
    fun deleteTournament(tournament: Tournament<*>)
}
