package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.lotteryValue
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The two tie-breaks of the EGF list pairgoth was missing: Previous Order (the players' relative
 * order at an earlier time, fed by the API) and Lottery (the last resort — a stable draw, since
 * the standings are recomputed at every request).
 */
class PreviousOrderLotteryTest : TestBase() {

    private fun aTournament(vararg criteria: String) = Json.Object(
        "type" to "INDIVIDUAL", "name" to "Tie", "shortName" to "tie",
        "startDate" to "2026-08-12", "endDate" to "2026-08-12",
        "country" to "FR", "location" to "Paris", "online" to false,
        "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
        "rounds" to 1,
        "pairing" to Json.Object("type" to "SWISS", "placement" to Json.Array(*criteria))
    )

    private fun threeTiedPlayers(vararg criteria: String): Pair<Int, List<Int>> {
        MemoryStore.reset()
        val tourId = TestAPI.post("/api/tour", aTournament(*criteria)).asObject().getInt("id") ?: fail("no tournament id")
        val ids = listOf("Aaa", "Bbb", "Ccc").map { name ->
            TestAPI.post("/api/tour/$tourId/part", Json.Object("name" to name, "firstname" to "X",
                "rating" to 1800, "rank" to -2, "country" to "FR", "club" to "13Ma", "final" to true))
                .asObject().getInt("id") ?: fail("no player id")
        }
        return tourId to ids
    }

    private fun order(tourId: Int) = TestAPI.get("/api/tour/$tourId/standings/0").asArray()
        .map { (it as Json.Object).getInt("id")!! }

    @Test
    fun `previous order breaks a tie, unknown players last`() {
        val (tourId, ids) = threeTiedPlayers("NBW", "PREV")
        // Ccc qualified 1st, Aaa 2nd, Bbb did not qualify
        TestAPI.put("/api/tour/$tourId/part/${ids[2]}", Json.Object("previousOrder" to 1)).asObject()
            .also { assertTrue(it.getBoolean("success")!!) }
        TestAPI.put("/api/tour/$tourId/part/${ids[0]}", Json.Object("previousOrder" to 2))
        assertEquals(listOf(ids[2], ids[0], ids[1]), order(tourId))
        // and the order survives a round trip through the store
        assertEquals(2, TestAPI.get("/api/tour/$tourId/part/${ids[0]}").asObject().getInt("previousOrder"))
    }

    @Test
    fun `the lottery is stable and separates every player`() {
        val (tourId, ids) = threeTiedPlayers("NBW", "LOTTERY")
        val drawn = order(tourId)
        assertEquals(drawn, order(tourId), "the same lots must come out at every computation")
        assertEquals(ids.toSet(), drawn.toSet())
        val lots = ids.map { lotteryValue(it) }
        assertEquals(lots.size, lots.toSet().size, "distinct players get distinct lots")
        assertTrue(lots.all { it >= 0.0 && it < 100.0 })
        // players are ordered by decreasing lot
        assertEquals(drawn, ids.sortedByDescending { lotteryValue(it) })
    }

    @Test
    fun `a lottery is not the registration order`() {
        // 20 players: the draw must not simply follow the ids
        val ids = (1..20).toList()
        assertNotEquals(ids, ids.sortedByDescending { lotteryValue(it) })
    }
}
