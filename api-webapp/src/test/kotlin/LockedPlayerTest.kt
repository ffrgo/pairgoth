package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level lock: a locked player's rating/rank/pro survive bulk imports (website sync,
 * ratings refresh). The flag is set by the bulk payload itself (the website marks its
 * rating-exception players); an explicit locked:false unlocks and applies in one shot,
 * an absent flag never unlocks. Single-player PUT (manual edit) is not restricted.
 */
class LockedPlayerTest : TestBase() {

    companion object {
        val tournament = Json.Object(
            "type" to "INDIVIDUAL",
            "name" to "Locked Player Test",
            "shortName" to "locked-player-test",
            "startDate" to "2024-01-01",
            "endDate" to "2024-01-01",
            "country" to "FR",
            "location" to "Test Location",
            "online" to false,
            "timeSystem" to Json.Object("type" to "SUDDEN_DEATH", "mainTime" to 3600),
            "rounds" to 1,
            "pairing" to Json.Object("type" to "MAC_MAHON")
        )
    }

    @Test
    fun `locked player level survives bulk upserts until explicit unlock`() {
        var resp = TestAPI.post("/api/tour", tournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")

        // 1. Bulk insert carrying the lock: values + flag land together.
        var report = TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "name" to "Locke", "firstname" to "John", "country" to "FR", "club" to "13Ma",
            "rating" to 2350, "rank" to 3, "egf" to "12345678", "locked" to true
        ))).asObject()
        assertEquals(1, report.getArray("added")!!.size)
        var player = TestAPI.get("/api/tour/$tourId/part").asArray()[0] as Json.Object
        val id = player.getInt("id")!!
        assertEquals(2350, player.getInt("rating"))
        assertEquals(true, player.getBoolean("locked"))

        // 2. Bulk update without the flag (a ratings refresh): rating/rank/pro preserved.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "id" to id, "rating" to 2263, "rank" to 2, "pro" to 0
        ))).asObject()
        assertEquals(1, report.getArray("unchanged")!!.size)
        player = TestAPI.get("/api/tour/$tourId/part/$id").asObject()
        assertEquals(2350, player.getInt("rating"))
        assertEquals(3, player.getInt("rank"))
        assertEquals(true, player.getBoolean("locked"))

        // 3. Bulk update with locked:true (a website sync): still preserved.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "id" to id, "rating" to 2263, "rank" to 2, "locked" to true
        ))).asObject()
        assertEquals(1, report.getArray("unchanged")!!.size)
        player = TestAPI.get("/api/tour/$tourId/part/$id").asObject()
        assertEquals(2350, player.getInt("rating"))

        // 4. Manual single-player PUT bypasses the lock (referee sets the exception value).
        resp = TestAPI.put("/api/tour/$tourId/part/$id", Json.Object("id" to id, "rating" to 2400)).asObject()
        assertTrue(resp.getBoolean("success")!!)
        player = TestAPI.get("/api/tour/$tourId/part/$id").asObject()
        assertEquals(2400, player.getInt("rating"))
        assertEquals(true, player.getBoolean("locked"))

        // 5. Explicit locked:false unlocks and applies its values in one shot.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "id" to id, "rating" to 2263, "rank" to 2, "locked" to false
        ))).asObject()
        assertEquals(1, report.getArray("updated")!!.size)
        player = TestAPI.get("/api/tour/$tourId/part/$id").asObject()
        assertEquals(2263, player.getInt("rating"))
        assertEquals(2, player.getInt("rank"))
        assertEquals(null, player.getBoolean("locked"))

        // 6. Unlocked: a flagless bulk update applies again.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "id" to id, "rating" to 2199
        ))).asObject()
        assertEquals(1, report.getArray("updated")!!.size)
        player = TestAPI.get("/api/tour/$tourId/part/$id").asObject()
        assertEquals(2199, player.getInt("rating"))
    }
}
