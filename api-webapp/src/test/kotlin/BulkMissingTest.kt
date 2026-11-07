package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * A roster import carries the full source roster: pre-existing players it leaves untouched
 * have been removed on the source side and land in the journal's `missing` section — report
 * only, never deleted. Partial payloads (?reason=) can't tell removed from omitted: no report.
 */
class BulkMissingTest : TestBase() {

    companion object {
        val tournament = Json.Object(
            "type" to "INDIVIDUAL",
            "name" to "Bulk Missing Test",
            "shortName" to "bulk-missing-test",
            "startDate" to "2024-01-01",
            "endDate" to "2024-01-01",
            "country" to "FR",
            "location" to "Test Location",
            "online" to false,
            "timeSystem" to Json.Object("type" to "SUDDEN_DEATH", "mainTime" to 3600),
            "rounds" to 1,
            "pairing" to Json.Object("type" to "MAC_MAHON")
        )
        fun player(ext: String, name: String) = Json.Object(
            "name" to name, "firstname" to "Test", "country" to "FR", "club" to "13Ma",
            "rating" to 2100, "rank" to 1, "ext" to ext
        )
    }

    @Test
    fun `untouched players are reported missing on full sync, kept, and ignored on partial payloads`() {
        val resp = TestAPI.post("/api/tour", tournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")

        var report = TestAPI.post("/api/tour/$tourId/part",
            Json.Array(player("101", "Alpha"), player("102", "Beta"))).asObject()
        assertEquals(2, report.getArray("added")!!.size)
        assertEquals(0, report.getArray("missing")!!.size)

        // Beta gone from the source roster: reported missing but still registered.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(player("101", "Alpha"))).asObject()
        assertEquals(1, report.getArray("missing")!!.size)
        assertEquals("Beta Test", report.getArray("missing")!![0])
        assertEquals(2, TestAPI.get("/api/tour/$tourId/part").asArray().size)

        // Partial payload (ratings refresh): omission is not removal.
        report = TestAPI.post("/api/tour/$tourId/part?reason=ratings", Json.Array(player("101", "Alpha"))).asObject()
        assertEquals(0, report.getArray("missing")!!.size)
    }
}
