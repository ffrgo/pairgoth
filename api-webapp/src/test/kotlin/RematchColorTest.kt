package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * "Pairing the same players twice must be avoided by all means... If two players meet again, then
 * they get inverse colours." (EGF tournament system rules). With two players and two rounds the
 * rematch is unavoidable, so the colours must swap.
 */
class RematchColorTest : TestBase() {

    @Test
    fun `players meeting again get inverse colours`() {
        MemoryStore.reset()
        val tour = Json.Object(
            "type" to "INDIVIDUAL", "name" to "Rematch", "shortName" to "rematch",
            "startDate" to "2026-08-12", "endDate" to "2026-08-12",
            "country" to "FR", "location" to "Paris", "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
            "rounds" to 2,
            "pairing" to Json.Object("type" to "SWISS")
        )
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id") ?: fail("no tournament id")
        for (name in listOf("Aaa", "Bbb")) {
            TestAPI.post("/api/tour/$tourId/part", Json.Object("name" to name, "firstname" to "X",
                // same rating: no handicap, so colours are free
                "rating" to 1800, "rank" to -2, "country" to "FR", "club" to "13Ma", "final" to true))
        }
        val first = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0)!!
        assertEquals(0, first.getInt("h"), "the fixture must be an even game")
        TestAPI.put("/api/tour/$tourId/res/1", Json.Object("id" to first.getInt("id"), "result" to "w"))

        val second = TestAPI.post("/api/tour/$tourId/pair/2", Json.Array("all")).asArray().getObject(0)!!
        assertEquals(first.getInt("w"), second.getInt("b"), "last round's white takes black")
        assertEquals(first.getInt("b"), second.getInt("w"), "and last round's black takes white")
    }
}
