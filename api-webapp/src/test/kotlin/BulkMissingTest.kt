package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A roster import carries the full source roster: pre-existing players it leaves untouched
 * have been removed on the source side and land in the journal's `missing` section — kept,
 * never deleted, but unregistered from the rounds still open to them (paired rounds stay).
 * Partial payloads (?reason=) can't tell removed from omitted: no report, no change.
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
            "rounds" to 2,
            "pairing" to Json.Object("type" to "MAC_MAHON")
        )
        fun player(ext: String, name: String) = Json.Object(
            "name" to name, "firstname" to "Test", "country" to "FR", "club" to "13Ma",
            "rating" to 2100, "rank" to 1, "ext" to ext
        )
    }

    @Test
    fun `missing players are kept but unregistered from open rounds`() {
        val resp = TestAPI.post("/api/tour", tournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")

        var report = TestAPI.post("/api/tour/$tourId/part",
            Json.Array(player("101", "Alpha"), player("102", "Beta"))).asObject()
        assertEquals(2, report.getArray("added")!!.size)
        assertEquals(0, report.getArray("missing")!!.size)
        TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all"))

        // Beta gone from the source roster: kept and reported; the paired round 1 stays,
        // the open round 2 is unregistered and the journal entry says so.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(player("101", "Alpha"))).asObject()
        var entry = report.getArray("missing")!![0] as Json.Object
        assertEquals("Beta Test", entry.getString("player"))
        assertEquals("unregistered from round 2", entry.getString("changes"))
        val players = TestAPI.get("/api/tour/$tourId/part").asArray()
        assertEquals(2, players.size)
        val beta = players.map { it as Json.Object }.first { it.getString("name") == "Beta" }
        assertEquals(listOf(2), beta.getArray("skip")!!.map { (it as Number).toInt() })

        // Resync: still missing, nothing left to unregister — no change reported.
        report = TestAPI.post("/api/tour/$tourId/part", Json.Array(player("101", "Alpha"))).asObject()
        entry = report.getArray("missing")!![0] as Json.Object
        assertEquals("Beta Test", entry.getString("player"))
        assertNull(entry.getString("changes"))

        // Partial payload (ratings refresh): omission is not removal.
        report = TestAPI.post("/api/tour/$tourId/part?reason=ratings", Json.Array(player("101", "Alpha"))).asObject()
        assertEquals(0, report.getArray("missing")!!.size)
    }

    // Team tournaments register on paper — the website cannot register teams, so its roster
    // (typically empty) is not authoritative: a sync must never unregister anyone. An operator
    // once pressed Sync on the Nations Cup and every player got all rounds skipped.
    @Test
    fun `a sync must not unregister players of a team tournament`() {
        val teamTournament = Json.MutableObject(tournament).also {
            it["type"] = "TEAM2"; it["shortName"] = "bulk-missing-team-test"
        }
        val tourId = TestAPI.post("/api/tour", teamTournament).asObject().getInt("id")
            ?: throw Error("tournament creation failed")
        TestAPI.post("/api/tour/$tourId/part",
            Json.Array(player("201", "Gamma"), player("202", "Delta"))).asObject()

        // the Nations Cup scenario: sync against an empty website roster
        val report = TestAPI.post("/api/tour/$tourId/part", Json.Array()).asObject()
        assertEquals(0, report.getArray("missing")!!.size)
        TestAPI.get("/api/tour/$tourId/part").asArray().map { it as Json.Object }.forEach {
            assertNull(it.getArray("skip"), "${it.getString("name")} must keep all rounds")
        }
    }
}
