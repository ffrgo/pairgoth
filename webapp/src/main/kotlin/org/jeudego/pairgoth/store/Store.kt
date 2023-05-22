package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.E

object Store {
    private val _nextTournamentId = AtomicInteger()
    private val _nextPlayerId = AtomicInteger()
    private val _nextGameId = AtomicInteger()
    val nextTournamentId get() = _nextTournamentId.incrementAndGet()
    val nextPlayerId get() = _nextPlayerId.incrementAndGet()
    val nextGameId get() = _nextGameId.incrementAndGet()

    private val tournaments = mutableMapOf<Int, Tournament<*>>()

    fun addTournament(tournament: Tournament<*>) {
        if (tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} already exists")
        tournaments[tournament.id] = tournament
    }

    fun getTournament(id: Int) = tournaments[id]

    fun getTournamentsIDs(): Set<Int> = tournaments.keys

    fun replaceTournament(tournament: Tournament<*>) {
        if (!tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} not known")
        tournaments[tournament.id] = tournament
    }

    fun deleteTournament(tournament: Tournament<*>) {
        if (!tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} not known")
        tournaments.remove(tournament.id)

    }
}
