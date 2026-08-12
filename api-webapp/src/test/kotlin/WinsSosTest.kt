package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.BLACK
import org.jeudego.pairgoth.model.Game.Result.WHITE
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SOSW/SOSOSW/SODOSW are wins-based and handicap-free (Swiss semantics), so a MacMahon
 * tournament ranked on NBW can use them as genuine swiss tie-breaks.
 */
class WinsSosTest: TestBase() {

    // 4 players, 2 rounds, handicap games; MM-like scores injected to prove independence
    // r1: 1 beats 2 (h2), 3 beats 4 (h0)  ->  wins 1:1 2:0 3:1 4:0
    // r2: 3 beats 1 (h3), 2 beats 4 (h0)  ->  wins 1:1 2:1 3:2 4:0
    private fun history() = listOf(
        listOf(
            Game(id = 1, table = 1, white = 1, black = 2, handicap = 2, result = WHITE),
            Game(id = 2, table = 2, white = 3, black = 4, handicap = 0, result = WHITE),
        ),
        listOf(
            Game(id = 3, table = 1, white = 1, black = 3, handicap = 3, result = BLACK),
            Game(id = 4, table = 2, white = 2, black = 4, handicap = 0, result = WHITE),
        ),
    )

    private fun helper() = HistoryHelper(history()).apply {
        // MM-like main scores (mmBase 10 + wins), never expected in the wins-based maps
        scoresFactory = { mapOf(1 to 11.0, 2 to 11.0, 3 to 12.0, 4 to 10.0) }
        scoresXFactory = scoresFactory
        missedRoundsSosFactory = { mapOf(1 to 10.0, 2 to 10.0, 3 to 10.0, 4 to 10.0) }
    }

    @Test
    fun `100 winsSos sums opponents wins, ignores handicap and scores`() {
        val h = helper()
        // final wins 1:1 2:1 3:2 4:0; 1 met 2,3 -> 1+2; 2 met 1,4 -> 1+0; 3 met 4,1 -> 0+1; 4 met 3,2 -> 2+1
        assertEquals(mapOf(1 to 3.0, 2 to 1.0, 3 to 1.0, 4 to 3.0), h.winsSos)
    }

    @Test
    fun `101 winsSosos sums opponents winsSos`() {
        val h = helper()
        // 1 met 2,3 -> 1+1; 2 met 1,4 -> 3+3; 3 met 4,1 -> 3+3; 4 met 3,2 -> 1+1
        assertEquals(mapOf(1 to 2.0, 2 to 6.0, 3 to 6.0, 4 to 2.0), h.winsSosos)
    }

    @Test
    fun `105 winsSosm1 and winsSosm2 drop the smallest round contributions`() {
        val h = helper()
        // opponent-wins lists: 1:[1,2] 2:[1,0] 3:[0,1] 4:[2,1]
        assertEquals(mapOf(1 to 2.0, 2 to 1.0, 3 to 1.0, 4 to 2.0), h.winsSosm1)
        assertEquals(mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0, 4 to 0.0), h.winsSosm2)
    }

    @Test
    fun `102 winsSodos sums defeated opponents wins only`() {
        val h = helper()
        // 1 beat 2 -> 1; 2 beat 4 -> 0; 3 beat 4,1 -> 0+1; 4 beat nobody
        assertEquals(mapOf(1 to 1.0, 2 to 0.0, 3 to 1.0, 4 to 0.0), h.winsSodos)
    }

    @Test
    fun `103 score-based sos still handicap-adjusted, proving the two families differ`() {
        val h = helper()
        // player 1's score-based sos = scores[2]+h2 read from white pov... = (11+2)+(12+3) = 28
        // vs wins-based 3.0 — the families genuinely diverge on handicapped MM data
        assert(h.sos[1] != h.winsSos[1])
    }

    @Test
    fun `104 bye games count zero in wins-based maps`() {
        val h = HistoryHelper(listOf(listOf(
            Game(id = 1, table = 1, white = 1, black = 2, handicap = 0, result = WHITE),
            Game(id = 2, table = 2, white = 0, black = 3, handicap = 0, result = BLACK),
        ))).apply {
            scoresFactory = { mapOf(1 to 1.0, 2 to 0.0, 3 to 1.0) }
            scoresXFactory = scoresFactory
            missedRoundsSosFactory = { mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0) }
        }
        assertEquals(0.0, h.winsSos[3])
        assertEquals(null, h.winsSodos[3])
    }

    // The standings path (ApiTools.getSortedPairables) has its own criterion table, separate
    // from BasePairingHelper.evalCriterion — this is the path the standings tab and the
    // exports go through, so it must serve the wins-based family too.
    @Test
    fun `200 standings serve wins-based SOSW and SOSOSW in a handicapped MacMahon`() {
        val tour = Json.Object(
            "type" to "INDIVIDUAL", "name" to "HFree", "shortName" to "hfree",
            "startDate" to "2026-08-03", "endDate" to "2026-08-05",
            "country" to "TR", "location" to "Ankara", "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
            "rounds" to 2,
            "pairing" to Json.Object(
                "type" to "MAC_MAHON",
                "handicap" to Json.Object("correction" to 1),
                "placement" to Json.Array("NBW", "SOSW", "SOSOSW")
            )
        )
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id")!!
        for (p in listOf(
            Json.Object("name" to "Strong", "firstname" to "S", "rating" to 1950, "rank" to -1, "country" to "TR", "club" to "Ank", "final" to true),
            Json.Object("name" to "Weak", "firstname" to "W", "rating" to 1550, "rank" to -5, "country" to "TR", "club" to "Ank", "final" to true),
        )) TestAPI.post("/api/tour/$tourId/part", p).asObject().also { assertTrue(it.getBoolean("success")!!) }

        val games = mutableListOf<Json.Object>()
        for (round in 1..2) {
            val game = TestAPI.post("/api/tour/$tourId/pair/$round", Json.Array("all")).asArray().getObject(0)!!
            TestAPI.put("/api/tour/$tourId/res/$round", Json.parse("""{"id":${game.getInt("id")},"result":"w"}""")).asObject()
            games.add(game)
        }
        assertTrue(games.any { it.getInt("h")!! > 0 }, "fixture must contain a handicap game")

        // white won every game: compute wins-based SOS from the games alone
        val wins = games.groupingBy { it.getInt("w")!! }.eachCount().mapValues { it.value.toDouble() }
        val standings = TestAPI.get("/api/tour/$tourId/standings/2").asArray().map { it as Json.Object }
        for (row in standings) {
            val id = row.getInt("id")!!
            val opponents = games.map { if (it.getInt("w") == id) it.getInt("b")!! else it.getInt("w")!! }
            assertEquals(opponents.sumOf { wins[it] ?: 0.0 }, row.getDouble("SOSW"), "SOSW must sum opponents' wins")
            assertEquals(opponents.sumOf { opp ->
                games.map { if (it.getInt("w") == opp) it.getInt("b")!! else it.getInt("w")!! }.sumOf { wins[it] ?: 0.0 }
            }, row.getDouble("SOSOSW"), "SOSOSW must sum opponents' SOSW")
        }
    }
}
