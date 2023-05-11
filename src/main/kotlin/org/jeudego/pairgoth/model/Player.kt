package org.jeudego.pairgoth.model

class Player(
    id: Int,
    name: String,
    var firstname: String,
    rating: Double,
    rank: Int,
    var country: String,
    var club: String
): Pairable(id, name, rating, rank) {
    companion object
    // used to store external IDs ("FFG" => FFG ID, "EGF" => EGF PIN, "AGA" => AGA ID ...)
    val externalIds = mutableMapOf<String, String>()
}
