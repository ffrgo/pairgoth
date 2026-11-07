package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * lastSync/lastPairing (and lastAction) are runtime stamps carried by the tournament instance.
 * A settings PUT rebuilds the instance through Tournament.fromJson(payload, existing) — the
 * stamps must be inherited from the existing instance, not silently reset (they drive the
 * standings tab's "unsynced pairing" confirm via the derived syncNeeded flag).
 */
class SyncStampsTest : TestBase() {

    @Test
    fun `a settings edit must not reset the sync stamps`() {
        MemoryStore.reset()
        val tourId = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: fail("no tournament id")
        TestAPI.post("/api/tour/$tourId/part", BasicTests.aPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/part", BasicTests.anotherPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all"))
        assertTrue(TestAPI.get("/api/tour/$tourId").asObject().getBoolean("syncNeeded")!!,
            "pairing must stamp lastPairing")
        // a sparse criteria change (the standings tab) rebuilds the tournament instance
        TestAPI.put("/api/tour/$tourId", Json.Object(
            "pairing" to Json.Object("placement" to Json.Array("MMS", "SOSM", "SOSOSM"))))
        assertTrue(TestAPI.get("/api/tour/$tourId").asObject().getBoolean("syncNeeded")!!,
            "a settings edit must not wipe lastPairing/lastSync")
    }
}
