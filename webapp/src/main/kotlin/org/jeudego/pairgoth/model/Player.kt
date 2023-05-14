package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store

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

fun Player.Companion.fromJson(json: Json.Object) = Player(
    id = json.getInt("id") ?: Store.nextPlayerId,
    name = json.getString("name") ?: badRequest("missing name"),
    firstname = json.getString("firstname") ?: badRequest("missing firstname"),
    rating = json.getDouble("rating") ?: badRequest("missing rating"),
    rank = json.getInt("rank") ?: badRequest("missing rank"),
    country = json.getString("country") ?: badRequest("missing country"),
    club = json.getString("club") ?: ""
)

fun Player.toJson() = Json.Object(
    "id" to id,
    "name" to name,
    "firstname" to firstname,
    "rating" to rating,
    "rank" to rank,
    "country" to country,
    "club" to club
)
