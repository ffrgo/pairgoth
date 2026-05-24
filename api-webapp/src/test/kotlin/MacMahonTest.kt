package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacMahonTest {
    @Test
    fun `Mac Mahon with handicap`() {
        var resp = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")
        resp = TestAPI.post("/api/tour/$tourId/part", BasicTests.aPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val p1 = resp.getInt("id")!!
        resp = TestAPI.post("/api/tour/$tourId/part", BasicTests.anotherPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val p2 = resp.getInt("id")!!
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0) ?: throw Error("pairing failed")
        assertEquals(p2, game.getInt("w"))
        assertEquals(p1, game.getInt("b"))
        assertEquals(3, game.getInt("h"))
    }

    // Production (non-legacy) pairs by the rating-derived effective rank, NOT the honorary grade.
    // Both players carry the same grade (1d) but ratings 4 buckets apart (5k vs 1k): a grade-based
    // handicap would be 0; the effective-rank handicap is 3. Guards the B"/effRank wiring against
    // a regression that silently reverts pairing to the stored grade.
    @Test
    fun `handicap follows effective rank, not honorary grade`() {
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: throw Error("tournament creation failed")
        val weak = Json.Object("name" to "Weak", "firstname" to "P", "rating" to 1550, "rank" to 0, "country" to "FR", "club" to "Cl", "final" to true)
        val strong = Json.Object("name" to "Strong", "firstname" to "P", "rating" to 1950, "rank" to 0, "country" to "FR", "club" to "Cl", "final" to true)
        TestAPI.post("/api/tour/$tourId/part", weak).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/part", strong).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0) ?: throw Error("pairing failed")
        assertEquals(3, game.getInt("h"), "handicap must derive from rating (5k vs 1k), not the shared 1d grade")
    }

    // Standings for a round that exists in the schedule but isn't paired yet must not blow up:
    // historyHelper(round+1) used to reach games(round+1) via usedTables and throw "invalid round".
    @Test
    fun `standings for a not-yet-paired round does not error`() {
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: throw Error("tournament creation failed")
        TestAPI.post("/api/tour/$tourId/part", BasicTests.aPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/part", BasicTests.anotherPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray()
        // round 2 is in the schedule (rounds=2) but unpaired — this used to throw "invalid round"
        val standings = TestAPI.get("/api/tour/$tourId/standings/2").asArray()
        assertEquals(2, standings.size, "standings should list both players as of the last paired round")
    }

}