package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger

object MemoryStore: Store {
    private val tournaments = mutableMapOf<ID, Tournament<*>>()

    // for tests
    fun reset() {
        tournaments.clear()
        _nextTournamentId.set(0)
        _nextPlayerId.set(0)
       _nextGameId.set(0)
    }

    override fun getTournaments(): Map<ID, Map<String, String>> = tournaments.mapValues {
        mapOf("name" to it.value.shortName)
    }

    override fun addTournament(tournament: Tournament<*>) {
        if (tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} already exists")
        tournaments[tournament.id] = tournament
    }

    override fun getTournament(id: ID) = tournaments[id]

    override fun replaceTournament(tournament: Tournament<*>) {
        if (!tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} not known")
        tournaments[tournament.id] = tournament
    }

    override fun deleteTournament(tournament: Tournament<*>) {
        if (!tournaments.containsKey(tournament.id)) throw Error("tournament id #${tournament.id} not known")
        tournaments.remove(tournament.id)

    }
}
