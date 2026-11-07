package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Find another optimal pairing" on an odd field. The bye game the solver adds carries a result of
 * its own ('w', structurally), which used to count as a played game and switch the whole feature off
 * for every round with a bye.
 */
class AlternativePairingTest: TestBase() {

    private fun player(name: String, rating: Int) =
        Json.Object("name" to name, "firstname" to "P", "rating" to rating, "rank" to 0,
            "country" to "FR", "club" to "Cl", "final" to true)

    private fun aTournamentOf(players: Int): Int {
        val id = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject().getInt("id") ?: throw Error("tournament creation failed")
        repeat(players) { TestAPI.post("/api/tour/$id/part", player("Player$it", 2000 - 50 * it)) }
        return id
    }

    @Test
    fun `an odd field can still be navigated`() {
        val id = aTournamentOf(5)
        val games = TestAPI.post("/api/tour/$id/pair/1", Json.Array("all")).asArray()
        assertEquals(3, games.size, "two games and a bye")
        assertTrue(games.map { (it as Json.Object).getInt("b") }.contains(0), "the odd player gets a bye")
        assertTrue(TestAPI.get("/api/tour/$id/pair/1").asObject().getBoolean("canRepair")!!,
            "a bye is not a played game: the round can still be navigated")
    }

    @Test
    fun `a played game stops navigation`() {
        val id = aTournamentOf(5)
        val games = TestAPI.post("/api/tour/$id/pair/1", Json.Array("all")).asArray().map { it as Json.Object }
        val played = games.first { it.getInt("b") != 0 }
        TestAPI.put("/api/tour/$id/res/1", Json.Object("id" to played.getInt("id"), "result" to "b"))
        assertFalse(TestAPI.get("/api/tour/$id/pair/1").asObject().getBoolean("canRepair")!!,
            "a real result freezes the round's pairing")
    }
}
