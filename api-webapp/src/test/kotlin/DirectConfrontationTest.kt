package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.pairing.DirectConfrontation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DirectConfrontationTest: TestBase() {

    private fun key(vararg values: Double): (Int) -> List<Double> {
        val map = values.mapIndexed { i, v -> i + 1 to listOf(v) }.toMap()
        return { id -> map[id]!! }
    }

    @Test
    fun `010 dc matches the OpenGotha Confrontation reference example`() {
        // mw.go.confrontation.Confrontation.main(): 6 players, sc 4,3,4,4,1,2
        // cycle 1-2-3, plus 1>4, 3>4, 5>2; reference run ranks: 5,1,3,2,4,6 -> DC = 6 - rank
        val group = listOf(1, 2, 3, 4, 5, 6)
        val wins = setOf(3 to 1, 1 to 2, 2 to 3, 1 to 4, 3 to 4, 5 to 2)
        val dc = DirectConfrontation.dc(group, wins, key(4.0, 3.0, 4.0, 4.0, 1.0, 2.0))
        assertEquals(
            mapOf(1 to 4.0, 2 to 2.0, 3 to 4.0, 4 to 1.0, 5 to 5.0, 6 to 0.0),
            dc
        )
    }

    @Test
    fun `011 dc without cycles is a plain bottom-up peel`() {
        // A beats B beats C; D never played: D and C tie at the bottom on wins,
        // C ranks above D through the after-DC key
        val group = listOf(1, 2, 3, 4)
        val wins = setOf(1 to 2, 2 to 3)
        val dc = DirectConfrontation.dc(group, wins, key(0.0, 0.0, 2.0, 1.0))
        assertEquals(mapOf(1 to 3.0, 2 to 2.0, 3 to 1.0, 4 to 0.0), dc)
    }

    @Test
    fun `012 dc full cycle is ordered by the after key alone`() {
        val group = listOf(1, 2, 3)
        val wins = setOf(1 to 2, 2 to 3, 3 to 1)
        val dc = DirectConfrontation.dc(group, wins, key(1.0, 3.0, 2.0))
        assertEquals(mapOf(1 to 0.0, 2 to 2.0, 3 to 1.0), dc)
    }

    @Test
    fun `013 dc ties share the same value`() {
        val group = listOf(1, 2, 3)
        val wins = setOf(1 to 2, 1 to 3)
        // 2 and 3 leave in one batch sharing rank 2, so the shared bottom value is 0
        // and the winner gets maxRank - 1 = 1 (OpenGotha rank compression)
        val dc = DirectConfrontation.dc(group, wins, key(0.0, 1.0, 1.0))
        assertEquals(mapOf(1 to 1.0, 2 to 0.0, 3 to 0.0), dc)
    }

    @Test
    fun `020 sdc counts beaten opponents when every pair has a decisive result`() {
        val group = listOf(1, 2, 3)
        val wins = setOf(1 to 2, 1 to 3, 2 to 3)
        assertEquals(
            mapOf(1 to 2.0, 2 to 1.0, 3 to 0.0),
            DirectConfrontation.sdc(group, wins)
        )
    }

    @Test
    fun `021 sdc is all zero on an incomplete matrix`() {
        val group = listOf(1, 2, 3)
        val wins = setOf(1 to 2, 1 to 3) // 2-3 never decided
        assertEquals(
            mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0),
            DirectConfrontation.sdc(group, wins)
        )
    }

    @Test
    fun `030 netWins ignores handicap games and cancels split results`() {
        val games = listOf(
            game(white = 1, black = 2, result = Game.Result.WHITE),               // 1 > 2
            game(white = 3, black = 1, result = Game.Result.WHITE, handicap = 2), // handicap: ignored
            game(white = 2, black = 3, result = Game.Result.BLACK),               // 3 > 2
            game(white = 3, black = 2, result = Game.Result.BLACK),               // 2 > 3: cancels
            game(white = 1, black = 3, result = Game.Result.JIGO),                // no winner
            game(white = 4, black = 1, result = Game.Result.WHITE),               // 4 not in group
            game(white = 0, black = 2, result = Game.Result.BLACK)                // bye
        )
        assertEquals(
            setOf(1 to 2),
            DirectConfrontation.netWins(games, setOf(1, 2, 3))
        )
    }

    private var nextGameId = 1
    private fun game(white: Int, black: Int, result: Game.Result, handicap: Int = 0) =
        Game(id = nextGameId++, table = nextGameId, white = white, black = black, handicap = handicap, result = result)

    @Test
    fun `040 end-to-end - DC orders head-to-head winners within tied groups`() {
        val tourId = TestAPI.post("/api/tour", Json.Object(
            "type" to "INDIVIDUAL",
            "name" to "DC Swiss",
            "shortName" to "dc-swiss",
            "startDate" to "2026-07-01",
            "endDate" to "2026-07-03",
            "country" to "FR",
            "location" to "Grenoble",
            "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 1800, "increment" to 15),
            "rounds" to 3,
            "pairing" to Json.Object("type" to "SWISS")
        )).asObject().getInt("id")!!
        TestAPI.put("/api/tour/$tourId", Json.Object(
            "pairing" to Json.Object("placement" to Json.Array("NBW", "DC", "SOSW", "SOSOSW"))
        ))

        // the intended DC losers (B over A, D over C) get the HIGHER ratings, so the final
        // order proves DC acted: without it the rating tiebreak would invert both pairs
        val ids = listOf("A" to 1800, "B" to 1900, "C" to 1600, "D" to 1700).associate { (name, rating) ->
            name to TestAPI.post("/api/tour/$tourId/part", Json.Object(
                "name" to name, "firstname" to "p", "rating" to rating,
                "rank" to (rating - 2050) / 100, "country" to "FR", "club" to "13Ma", "final" to true
            )).asObject().getInt("id")!!
        }
        // full round robin over 3 rounds; intended winners: A>B, A>C, D>A, B>C, B>D, C>D
        // -> NBW: A 2, B 2, C 1, D 1; head-to-head: A beat B, C beat D... (C>D above)
        val winners = mapOf(
            setOf("A", "B") to "A", setOf("A", "C") to "A", setOf("A", "D") to "D",
            setOf("B", "C") to "B", setOf("B", "D") to "B", setOf("C", "D") to "C"
        )
        val byId = ids.entries.associate { (name, id) -> id to name }
        for (round in 1..3) {
            TestAPI.post("/api/tour/$tourId/pair/$round", Json.Array("all"))
            TestAPI.get("/api/tour/$tourId/res/$round").asArray().forEach { g ->
                g as Json.Object
                val white = byId[g.getInt("w")!!]!!
                val black = byId[g.getInt("b")!!]!!
                val result = if (winners[setOf(white, black)] == white) "w" else "b"
                TestAPI.put("/api/tour/$tourId/res/$round", Json.parse("""{"id":${g.getInt("id")},"result":"$result"}"""))
            }
        }

        val standings = TestAPI.get("/api/tour/$tourId/standings/3").asArray().map {
            (it as Json.Object).getString("name")!!
        }
        // A above B (NBW 2, A beat B), C above D (NBW 1, C beat D), despite lower ratings
        assertEquals(listOf("A", "B", "C", "D"), standings)
    }
}
