package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `playing` is the highest round whose pairing went public — either explicitly stamped by the
 * client (print/publish) or implied by a result coming in. It guards destructive pairing actions
 * (the client confirms before unpairing a round players are sitting at), so it must be monotonic,
 * idempotent, range-checked, and survive the instance rebuild a settings PUT performs.
 */
class PlayingStampTest : TestBase() {

    private fun aPairedTournament(): Int {
        MemoryStore.reset()
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: fail("no tournament id")
        TestAPI.post("/api/tour/$tourId/part", BasicTests.aPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/part", BasicTests.anotherPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        return tourId
    }

    private fun playing(tourId: Int) = TestAPI.get("/api/tour/$tourId").asObject().getInt("playing")

    @Test
    fun `stamping a round is idempotent, monotonic and range-checked`() {
        val tourId = aPairedTournament()
        assertEquals(0, playing(tourId), "a fresh tournament is not playing anything")
        TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all"))
        assertEquals(0, playing(tourId), "pairing alone does not make the round public")

        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 1)).asObject().also {
            assertTrue(it.getBoolean("success")!!, "expecting success")
        }
        assertEquals(1, playing(tourId), "round 1 is now public")
        // a second print of the same round must not fail nor add a history row
        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 1)).asObject().also {
            assertTrue(it.getBoolean("success")!!, "re-stamping the same round must succeed")
        }
        assertEquals(1, playing(tourId))

        // out of the 1..rounds range (aMMTournament has 2 rounds)
        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 0)).asObject().also {
            assertTrue(it.getBoolean("success") == false, "round 0 must be refused")
            assertTrue(it.getString("error")!!.contains("playing"), "expecting the range guard's message")
        }
        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 3)).asObject().also {
            assertTrue(it.getBoolean("success") == false, "a round past the last one must be refused")
        }
        assertEquals(1, playing(tourId), "a refused stamp changes nothing")

        TestAPI.post("/api/tour/$tourId/pair/2", Json.Array("all"))
        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 2))
        assertEquals(2, playing(tourId))
        // going back to an earlier round is a silent no-op, not a rollback
        TestAPI.put("/api/tour/$tourId", Json.Object("playing" to 1)).asObject().also {
            assertTrue(it.getBoolean("success")!!, "a lower round is a no-op success")
        }
        assertEquals(2, playing(tourId), "playing only ever grows")

        // a settings PUT rebuilds the instance through Tournament.fromJson(payload, existing)
        TestAPI.put("/api/tour/$tourId", Json.Object(
            "pairing" to Json.Object("placement" to Json.Array("MMS", "SOSM", "SOSOSM"))))
        assertEquals(2, playing(tourId), "a settings edit must not wipe playing")
    }

    @Test
    fun `entering a result makes the round public`() {
        val tourId = aPairedTournament()
        val games = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray()
        val gameId = (games[0] as Json.Object).getInt("id") ?: fail("no game id")
        assertEquals(0, playing(tourId))
        // small tournaments never print the pairing — a result is the proof players are playing
        TestAPI.put("/api/tour/$tourId/res/1", Json.Object("id" to gameId, "result" to "b")).asObject().also {
            assertTrue(it.getBoolean("success")!!, "expecting success")
        }
        assertEquals(1, playing(tourId), "a result stamps its round")
    }
}
