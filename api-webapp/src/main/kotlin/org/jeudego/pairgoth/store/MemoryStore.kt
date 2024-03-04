package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament

class MemoryStore: Store {
    private val tournaments = mutableMapOf<ID, Tournament<*>>()

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
