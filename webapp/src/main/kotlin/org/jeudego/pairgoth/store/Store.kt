package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger

object Store {
    private val _nextTournamentId = AtomicInteger()
    private val _nextPlayerId = AtomicInteger()
    val nextTournamentId get() = _nextTournamentId.incrementAndGet()
    val nextPlayerId get() = _nextPlayerId.incrementAndGet()

    private val tournaments = mutableMapOf<Int, Tournament>()
    private val players = mutableMapOf<Int, Player>()

    fun addTournament(tournament: Tournament) {
        if (tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} already exists")
        tournaments[tournament.id] = tournament
    }

    fun getTournament(id: Int) = tournaments[id]

    fun getTournamentsIDs(): Set<Int> = tournaments.keys
}
