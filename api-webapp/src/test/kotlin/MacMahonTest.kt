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

    // exports can drop players who never played a real game. With 3 players the weakest gets
    // a bye in round 1, so only that bye-only player must disappear when drop_unplayed=true.
    @Test
    fun `export can drop players who never played a real game`() {
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: throw Error("tournament creation failed")
        listOf(
            Json.Object("name" to "Alpha", "firstname" to "A", "rating" to 1900, "rank" to -2, "country" to "FR", "club" to "C", "final" to true),
            Json.Object("name" to "Beta", "firstname" to "B", "rating" to 1850, "rank" to -2, "country" to "FR", "club" to "C", "final" to true),
            Json.Object("name" to "Ghost", "firstname" to "G", "rating" to 1000, "rank" to -11, "country" to "FR", "club" to "C", "final" to true)
        ).forEach { TestAPI.post("/api/tour/$tourId/part", it).asObject().also { r -> assertTrue(r.getBoolean("success")!!) } }
        TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray()
        // asserted via the JSON standings (same filtered list; CSV/EGF/FFG share it). One of the
        // three gets a bye in round 1, so drop_unplayed must remove exactly that bye-only player.
        val full = TestAPI.get("/api/tour/$tourId/standings/1").asArray()
        val filtered = TestAPI.get("/api/tour/$tourId/standings/1?drop_unplayed=true").asArray()
        assertEquals(3, full.size, "unfiltered standings list all three")
        assertEquals(2, filtered.size, "drop_unplayed removes the bye-only player")
    }

    // The frozen snapshot is the published FINAL standings. It must not hijack earlier rounds:
    // round-0 MMS is the mmBase the Mac Mahon groups dialog keys on, and used to come back
    // as final scores once the tournament was published (no 5d+ visible around the bar).
    @Test
    fun `frozen standings only serve the final round`() {
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: throw Error("tournament creation failed")
        // mmBar defaults to 0 (1d): Alpha 1d → mmBase 30, Beta 5k → mmBase 25
        val alpha = Json.Object("name" to "Alpha", "firstname" to "A", "rating" to 2050, "rank" to 0, "country" to "FR", "club" to "C", "final" to true)
        val beta = Json.Object("name" to "Beta", "firstname" to "B", "rating" to 1550, "rank" to -5, "country" to "FR", "club" to "C", "final" to true)
        val alphaId = TestAPI.post("/api/tour/$tourId/part", alpha).asObject().getInt("id")!!
        TestAPI.post("/api/tour/$tourId/part", beta).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0) ?: throw Error("pairing failed")
        val alphaRes = if (game.getInt("w") == alphaId) "w" else "b"
        TestAPI.put("/api/tour/$tourId/res/1", Json.parse("""{"id":${game.getInt("id")},"result":"$alphaRes"}""")).asObject()

        fun mms(standings: Json.Array, name: String) =
            standings.map { it as Json.Object }.first { it.getString("name") == name }.getDouble("MMS")

        val frozen = TestAPI.put("/api/tour/$tourId/standings", Json.Object()).asObject()
        assertTrue(frozen.getString("status") == "ok", "freeze failed")
        val finalStandings = TestAPI.get("/api/tour/$tourId/standings/2").asArray()

        val initial = TestAPI.get("/api/tour/$tourId/standings/0").asArray()
        assertEquals(30.0, mms(initial, "Alpha"), "round-0 MMS must be Alpha's mmBase, not the frozen final score")
        assertEquals(25.0, mms(initial, "Beta"), "round-0 MMS must be Beta's mmBase, not the frozen final score")

        // flip the result: the final round keeps serving the snapshot, earlier rounds recompute
        TestAPI.put("/api/tour/$tourId/res/1", Json.parse("""{"id":${game.getInt("id")},"result":"${if (alphaRes == "w") "b" else "w"}"}""")).asObject()
        assertEquals(mms(finalStandings, "Alpha"), mms(TestAPI.get("/api/tour/$tourId/standings/2").asArray(), "Alpha"), "final round must still serve the frozen snapshot")
        assertEquals(26.0, mms(TestAPI.get("/api/tour/$tourId/standings/1").asArray(), "Beta"), "intermediate rounds must recompute (Beta now has the win)")
    }

}
