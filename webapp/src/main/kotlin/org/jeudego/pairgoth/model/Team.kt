package org.jeudego.pairgoth.model

class Team(id: Int, name: String, rating: Double, rank: Int): Pairable(id, name, rating, rank) {
    companion object {}
    val players = mutableSetOf<Player>()
}
