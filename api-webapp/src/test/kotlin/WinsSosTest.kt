package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.BLACK
import org.jeudego.pairgoth.model.Game.Result.WHITE
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}
