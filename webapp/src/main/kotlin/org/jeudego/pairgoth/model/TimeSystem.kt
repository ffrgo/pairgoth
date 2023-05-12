package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler


sealed class TimeSystem(
    val type: TimeSystemType,
    val mainTime: Int,
    val increment: Int,
    val maxTime: Int = Int.MAX_VALUE,
    val byoyomi: Int,
    val periods: Int,
    val stones: Int
) {
    companion object {}
    enum class TimeSystemType { CANADIAN, STANDARD, FISHER, SUDDEN_DEATH }
}

class CanadianByoyomi(mainTime: Int, byoyomi: Int, stones: Int):
    TimeSystem(
        type = TimeSystemType.CANADIAN,
        mainTime = mainTime,
        increment = 0,
        byoyomi = byoyomi,
        periods = 1,
        stones = stones
    )

class StandardByoyomi(mainTime: Int, byoyomi: Int, periods: Int):
        TimeSystem(
            type = TimeSystemType.STANDARD,
            mainTime = mainTime,
            increment = 0,
            byoyomi = byoyomi,
            periods = periods,
            stones = 1
        )

class FisherTime(mainTime: Int, increment: Int, maxTime: Int = Int.MAX_VALUE):
        TimeSystem(
            type = TimeSystemType.FISHER,
            mainTime = mainTime,
            increment = increment,
            maxTime = maxTime,
            byoyomi = 0,
            periods = 0,
            stones = 0
        )

class SuddenDeath(mainTime: Int):
    TimeSystem(
        type = TimeSystemType.SUDDEN_DEATH,
        mainTime = mainTime,
        increment = 0,
        byoyomi = 0,
        periods = 0,
        stones = 0
    )

// Serialization

fun TimeSystem.Companion.fromJson(json: Json.Object) =
    when (json.getString("type")?.uppercase() ?: ApiHandler.badRequest("missing timeSystem type")) {
        "CANADIAN" -> CanadianByoyomi(
            mainTime = json.getInt("mainTime") ?: ApiHandler.badRequest("missing timeSystem mainTime"),
            byoyomi = json.getInt("byoyomi") ?: ApiHandler.badRequest("missing timeSystem byoyomi"),
            stones = json.getInt("stones") ?: ApiHandler.badRequest("missing timeSystem stones")
        )
        "STANDARD" -> StandardByoyomi(
            mainTime = json.getInt("mainTime") ?: ApiHandler.badRequest("missing timeSystem mainTime"),
            byoyomi = json.getInt("byoyomi") ?: ApiHandler.badRequest("missing timeSystem byoyomi"),
            periods = json.getInt("periods") ?: ApiHandler.badRequest("missing timeSystem periods")
        )
        "FISHER" -> FisherTime(
            mainTime = json.getInt("mainTime") ?: ApiHandler.badRequest("missing timeSystem mainTime"),
            increment = json.getInt("increment") ?: ApiHandler.badRequest("missing timeSystem increment"),
            maxTime = json.getInt("increment") ?: Integer.MAX_VALUE
        )
        "SUDDEN_DEATH" -> SuddenDeath(
            mainTime = json.getInt("mainTime") ?: ApiHandler.badRequest("missing timeSystem mainTime"),
        )
        else -> ApiHandler.badRequest("invalid or missing timeSystem type")
    }

fun TimeSystem.toJson() = when (type) {
    TimeSystem.TimeSystemType.CANADIAN -> Json.Object("mainTime" to mainTime, "byoyomi" to byoyomi, "stones" to stones)
    TimeSystem.TimeSystemType.STANDARD -> Json.Object("mainTime" to mainTime, "byoyomi" to byoyomi, "periods" to periods)
    TimeSystem.TimeSystemType.FISHER -> Json.Object("mainTime" to mainTime, "increment" to increment, "maxTime" to maxTime)
    TimeSystem.TimeSystemType.SUDDEN_DEATH -> Json.Object("mainTime" to mainTime)
}

