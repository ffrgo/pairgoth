package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import kotlinx.datetime.LocalDate
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store

enum class TournamentType(val playersNumber: Int) {
    INDIVIDUAL(1),
    PAIRGO(2),
    RENGO2(2),
    RENGO3(3),
    TEAM2(2),
    TEAM3(3),
    TEAM4(4),
    TEAM5(5);
}

data class Tournament(
    var id: Int,
    var type: TournamentType,
    var name: String,
    var shortName: String,
    var startDate: LocalDate,
    var endDate: LocalDate,
    var country: String,
    var location: String,
    var online: Boolean,
    var timeSystem: TimeSystem,
    var pairing: Pairing,
    var rules: Rules = Rules.FRENCH,
    var gobanSize: Int = 19,
    var komi: Double = 7.5
) {
    companion object
    // player/team id -> set of skipped rounds
    val pairables = mutableMapOf<Int, MutableSet<Int>>()
}

// Serialization

fun Tournament.Companion.fromJson(json: Json.Object) = Tournament(
    id = json.getInt("id") ?: Store.nextTournamentId,
    type = json.getString("type")?.uppercase()?.let { TournamentType.valueOf(it) } ?: badRequest("missing type"),
    name = json.getString("name") ?: ApiHandler.badRequest("missing name"),
    shortName = json.getString("shortName") ?: ApiHandler.badRequest("missing shortName"),
    startDate = json.getLocalDate("startDate") ?: ApiHandler.badRequest("missing startDate"),
    endDate = json.getLocalDate("endDate") ?: ApiHandler.badRequest("missing endDate"),
    country = json.getString("country") ?: ApiHandler.badRequest("missing country"),
    location = json.getString("location") ?: ApiHandler.badRequest("missing location"),
    online = json.getBoolean("online") ?: false,
    komi = json.getDouble("komi") ?: 7.5,
    rules = json.getString("rules")?.let { Rules.valueOf(it) } ?: Rules.FRENCH,
    gobanSize = json.getInt("gobanSize") ?: 19,
    timeSystem = TimeSystem.fromJson(json.getObject("timeSystem") ?: badRequest("missing timeSystem")),
    pairing = MacMahon()
)

fun Tournament.toJson() = Json.Object(
    "id" to id,
    "type" to type.name,
    "name" to name,
    "shortName" to shortName,
    "startDate" to startDate.toString(),
    "endDate" to endDate.toString(),
    "country" to country,
    "location" to location,
    "online" to online,
    "komi" to komi,
    "rules" to rules.name,
    "gobanSize" to gobanSize,
    "timeSystem" to timeSystem.toJson(),
    "pairing" to pairing.toJson()
)
