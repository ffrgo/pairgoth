package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * "The player does not play in a round of McMahon or Swiss and this is in agreement to all valid
 * tournament rulesets" is worth ½ (EGF tournament system rules). Mac-Mahon had `mmsValueAbsent`
 * for that; a swiss had nothing at all, and an OpenGotha file setting it was even refused on
 * import. `nbwValueAbsent` is the swiss counterpart — 0 by default, as in OpenGotha.
 */
class AbsentValueTest : TestBase() {

    private fun aSwiss(nbwValueAbsent: Double) = Json.Object(
        "type" to "INDIVIDUAL", "name" to "Absent", "shortName" to "absent",
        "startDate" to "2026-08-12", "endDate" to "2026-08-12",
        "country" to "FR", "location" to "Paris", "online" to false,
        "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
        "rounds" to 2,
        "pairing" to Json.Object(
            "type" to "SWISS",
            "main" to Json.Object("nbwValueAbsent" to nbwValueAbsent),
            "placement" to Json.Array("NBW", "SOSW")
        )
    )

    // Two players, two rounds; both sit out round 2 (nobody is paired).
    private fun standingsAfterASkippedRound(nbwValueAbsent: Double): List<Json.Object> {
        MemoryStore.reset()
        val tourId = TestAPI.post("/api/tour", aSwiss(nbwValueAbsent)).asObject().getInt("id") ?: fail("no tournament id")
        for (p in listOf("Aaa", "Bbb")) {
            TestAPI.post("/api/tour/$tourId/part", Json.Object("name" to p, "firstname" to "X",
                "rating" to 1800, "rank" to -2, "country" to "FR", "club" to "13Ma", "final" to true))
                .asObject().also { assertTrue(it.getBoolean("success")!!) }
        }
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0)!!
        TestAPI.put("/api/tour/$tourId/res/1", Json.Object("id" to game.getInt("id"), "result" to "w")).asObject()
        // round 2 is never paired: both players missed it
        return TestAPI.get("/api/tour/$tourId/standings/2").asArray().map { it as Json.Object }
    }

    @Test
    fun `by default a non-played round is worth nothing`() {
        val standings = standingsAfterASkippedRound(0.0)
        assertEquals(listOf(1.0, 0.0), standings.map { it.getDouble("NBW") })
    }

    @Test
    fun `half a point for a non-played round reaches the standings`() {
        val standings = standingsAfterASkippedRound(0.5)
        // 1 + ½ and 0 + ½, both rounded down (roundDownScore is on by default)
        assertEquals(listOf(1.0, 0.0), standings.map { it.getDouble("NBW") })
    }

    @Test
    fun `unrounded, the half point shows`() {
        MemoryStore.reset()
        val tour = aSwiss(0.5).toMutableMap().let { map ->
            val pairing = (map["pairing"] as Json.Object).toMutableMap()
            pairing["main"] = Json.Object("nbwValueAbsent" to 0.5, "roundDownScore" to false)
            map["pairing"] = Json.Object(pairing)
            Json.Object(map)
        }
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id") ?: fail("no tournament id")
        for (p in listOf("Aaa", "Bbb")) {
            TestAPI.post("/api/tour/$tourId/part", Json.Object("name" to p, "firstname" to "X",
                "rating" to 1800, "rank" to -2, "country" to "FR", "club" to "13Ma", "final" to true))
                .asObject().also { assertTrue(it.getBoolean("success")!!) }
        }
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0)!!
        TestAPI.put("/api/tour/$tourId/res/1", Json.Object("id" to game.getInt("id"), "result" to "w")).asObject()
        val standings = TestAPI.get("/api/tour/$tourId/standings/2").asArray().map { it as Json.Object }
        assertEquals(listOf(1.5, 0.5), standings.map { it.getDouble("NBW") })
        // and the SOS family sums those scores, not the raw win counts
        assertEquals(listOf(0.5, 1.5), standings.map { it.getDouble("SOSW") })
    }
}
