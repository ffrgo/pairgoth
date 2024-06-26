package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.TimeSystem.TimeSystemType.*

data class TimeSystem(
    val type: TimeSystemType,
    val mainTime: Int,
    val increment: Int,
    val maxTime: Int = 0,
    val byoyomi: Int,
    val periods: Int,
    val stones: Int
) {
    companion object {}
    enum class TimeSystemType { CANADIAN, JAPANESE, FISCHER, SUDDEN_DEATH }
}

fun CanadianByoyomi(mainTime: Int, byoyomi: Int, stones: Int) =
    TimeSystem(
        type = CANADIAN,
        mainTime = mainTime,
        increment = 0,
        byoyomi = byoyomi,
        periods = 1,
        stones = stones
    )

fun StandardByoyomi(mainTime: Int, byoyomi: Int, periods: Int) =
        TimeSystem(
            type = JAPANESE,
            mainTime = mainTime,
            increment = 0,
            byoyomi = byoyomi,
            periods = periods,
            stones = 1
        )

fun FischerTime(mainTime: Int, increment: Int, maxTime: Int = 0) =
        TimeSystem(
            type = FISCHER,
            mainTime = mainTime,
            increment = increment,
            maxTime = maxTime,
            byoyomi = 0,
            periods = 0,
            stones = 0
        )

fun SuddenDeath(mainTime: Int) =
    TimeSystem(
        type = SUDDEN_DEATH,
        mainTime = mainTime,
        increment = 0,
        byoyomi = 0,
        periods = 0,
        stones = 0
    )

// Serialization

fun TimeSystem.Companion.fromJson(json: Json.Object) =
    when (json.getString("type")?.uppercase() ?: badRequest("missing timeSystem type")) {
        "CANADIAN" -> CanadianByoyomi(
            mainTime = json.getInt("mainTime") ?: badRequest("missing timeSystem mainTime"),
            byoyomi = json.getInt("byoyomi") ?: badRequest("missing timeSystem byoyomi"),
            stones = json.getInt("stones") ?: badRequest("missing timeSystem stones")
        )
        "STANDARD", "JAPANESE" -> StandardByoyomi(
            mainTime = json.getInt("mainTime") ?: badRequest("missing timeSystem mainTime"),
            byoyomi = json.getInt("byoyomi") ?: badRequest("missing timeSystem byoyomi"),
            periods = json.getInt("periods") ?: badRequest("missing timeSystem periods")
        )
        "FISCHER" -> FischerTime(
            mainTime = json.getInt("mainTime") ?: badRequest("missing timeSystem mainTime"),
            increment = json.getInt("increment") ?: badRequest("missing timeSystem increment"),
            maxTime = json.getInt("maxTime")?.let { if (it == Int.MAX_VALUE || it == -1) 0 else it } ?: 0
        )
        "SUDDEN_DEATH" -> SuddenDeath(
            mainTime = json.getInt("mainTime") ?: badRequest("missing timeSystem mainTime"),
        )
        else -> badRequest("invalid or missing timeSystem type")
    }

fun TimeSystem.toJson() = when (type) {
    TimeSystem.TimeSystemType.CANADIAN -> Json.Object("type" to type.name, "mainTime" to mainTime, "byoyomi" to byoyomi, "stones" to stones)
    TimeSystem.TimeSystemType.JAPANESE -> Json.Object("type" to type.name, "mainTime" to mainTime, "byoyomi" to byoyomi, "periods" to periods)
    TimeSystem.TimeSystemType.FISCHER ->
        if (maxTime == Int.MAX_VALUE || maxTime == -1 || maxTime == 0) Json.Object("type" to type.name, "mainTime" to mainTime, "increment" to increment)
        else Json.Object("type" to type.name, "mainTime" to mainTime, "increment" to increment, "maxTime" to maxTime)
    TimeSystem.TimeSystemType.SUDDEN_DEATH -> Json.Object("type" to type.name, "mainTime" to mainTime)
}

fun TimeSystem.adjustedTime() = when (type) {
    TimeSystem.TimeSystemType.CANADIAN -> mainTime + 60 * byoyomi / stones
    TimeSystem.TimeSystemType.JAPANESE -> mainTime + 45 * byoyomi
    TimeSystem.TimeSystemType.FISCHER -> mainTime + 120 * increment
    TimeSystem.TimeSystemType.SUDDEN_DEATH -> mainTime
}
