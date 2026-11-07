package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.BLACK
import org.jeudego.pairgoth.model.Game.Result.JIGO
import org.jeudego.pairgoth.model.Game.Result.WHITE
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A jigo is worth half a point to each player (EGF tournament system rules, "Game Result": 1/2 for
 * a tie by the rules of play or by arbitration). It used to score zero everywhere but in EGFDC.
 */
class JigoTest : TestBase() {

    // r1: 1 beats 2, 3 jigo 4    r2: 1 jigo 3, 2 beats 4  ->  wins 1:1½ 2:1 3:1 4:½
    private fun helper() = HistoryHelper(listOf(
        listOf(
            Game(id = 1, table = 1, white = 1, black = 2, result = WHITE),
            Game(id = 2, table = 2, white = 3, black = 4, result = JIGO),
        ),
        listOf(
            Game(id = 3, table = 1, white = 1, black = 3, result = JIGO),
            Game(id = 4, table = 2, white = 2, black = 4, result = WHITE),
        ),
    )).apply {
        // MacMahon-like scores, base 10
        scoresFactory = { mapOf(1 to 11.5, 2 to 11.0, 3 to 11.0, 4 to 10.5) }
        scoresXFactory = scoresFactory
        missedRoundsSosFactory = { mapOf(1 to 10.0, 2 to 10.0, 3 to 10.0, 4 to 10.0) }
    }

    @Test
    fun `100 a jigo brings half a point to each player`() {
        assertEquals(mapOf(1 to 1.5, 2 to 1.0, 3 to 1.0, 4 to 0.5), helper().wins)
    }

    @Test
    fun `101 sum of defeated opponents counts half of a jigo opponent`() {
        val h = helper()
        // player 1: beat 2 (score 11.0) and drew with 3 (11.0 x ½)
        assertEquals(11.0 + 5.5, h.sodos[1])
        // wins-based flavour, same weighting over opponents' wins
        assertEquals(1.0 + 0.5 * 1.0, h.winsSodos[1])
        // player 4 drew with 3 and lost to 2: half of 3's score, nothing for the loss
        assertEquals(5.5, h.sodos[4])
    }

    @Test
    fun `102 sum of opponents scores is unchanged by who won`() {
        val h = helper()
        assertEquals(11.0 + 11.0, h.sos[1])
        assertEquals(1.0 + 1.0, h.winsSos[1])
    }

    @Test
    fun `200 standings credit a jigo in NBW, MMS and SOS`() {
        MemoryStore.reset()
        val tour = Json.Object(
            "type" to "INDIVIDUAL", "name" to "Jigo", "shortName" to "jigo",
            "startDate" to "2026-08-12", "endDate" to "2026-08-12",
            "country" to "FR", "location" to "Paris", "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
            "rounds" to 1,
            "pairing" to Json.Object(
                "type" to "MAC_MAHON",
                // no rounding, so the half point stays visible in the standings
                "main" to Json.Object("roundDownScore" to false),
                // even game, so SOS is not handicap-shifted
                "handicap" to Json.Object("ceiling" to 0),
                "placement" to Json.Array("MMS", "SOSM", "NBW")
            )
        )
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id") ?: fail("no tournament id")
        for (p in listOf(BasicTests.aPlayer, BasicTests.anotherPlayer)) {
            TestAPI.post("/api/tour/$tourId/part", (p.toMutableMap().also { it["final"] = true }).let { Json.Object(it) })
                .asObject().also { assertTrue(it.getBoolean("success")!!) }
        }
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0)!!
        TestAPI.put("/api/tour/$tourId/res/1", Json.Object("id" to game.getInt("id"), "result" to "=")).asObject()

        val standings = TestAPI.get("/api/tour/$tourId/standings/1").asArray().map { it as Json.Object }
        assertEquals(2, standings.size)
        standings.forEach { row ->
            assertEquals(0.5, row.getDouble("NBW"), "half a point for the jigo")
            // MacMahon base (floor..bar) + half a point
            assertEquals(0.5, row.getDouble("MMS")!! % 1.0, "the half point reaches the MacMahon score")
        }
        // both drew with each other, so each one's SOS is the other's MMS
        assertEquals(standings[1].getDouble("MMS"), standings[0].getDouble("SOSM"))
    }
}
