package org.jeudego.pairgoth.test

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
}
