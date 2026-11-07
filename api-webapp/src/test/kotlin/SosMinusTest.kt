package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.BLACK
import org.jeudego.pairgoth.model.Game.Result.WHITE
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * "SOS-1 = SOS, where 1 round with the smallest value is ignored" (EGF tournament system rules;
 * OpenGotha drops the same round). Pairgoth used to drop the *greatest* contribution, and to
 * consider played games only — a missed round, worth the player's own starting score in a
 * Mac-Mahon and 0 in a swiss, is a round like any other and can be the discarded one.
 */
class SosMinusTest : TestBase() {

    // 3 rounds, players 1 and 4 miss round 2 (no game at all for them that round)
    private fun history() = listOf(
        listOf(
            Game(id = 1, table = 1, white = 1, black = 2, result = WHITE),
            Game(id = 2, table = 2, white = 3, black = 4, result = WHITE),
        ),
        listOf(
            Game(id = 3, table = 1, white = 3, black = 2, result = WHITE),
        ),
        listOf(
            Game(id = 4, table = 1, white = 1, black = 3, result = BLACK),
            Game(id = 5, table = 2, white = 2, black = 4, result = WHITE),
        ),
    )

    private fun helper() = HistoryHelper(history()).apply {
        scoresFactory = { mapOf(1 to 12.0, 2 to 11.0, 3 to 13.0, 4 to 10.0) }
        scoresXFactory = scoresFactory
        // Mac-Mahon: a missed round is worth the player's own starting score
        missedRoundsSosFactory = { mapOf(1 to 10.0, 2 to 10.0, 3 to 10.0, 4 to 10.0) }
    }

    @Test
    fun `a missed round is a contribution like any other`() {
        // player 1: [11 (opp 2), 10 (missed), 13 (opp 3)]
        assertEquals(34.0, helper().sos[1])
    }

    @Test
    fun `sos-1 and sos-2 ignore the smallest rounds`() {
        val h = helper()
        // 1: [11,10,13] 2: [12,13,10] 3: [10,11,12] 4: [13,10,11]
        assertEquals(mapOf(1 to 24.0, 2 to 25.0, 3 to 23.0, 4 to 24.0), h.sosm1)
        assertEquals(mapOf(1 to 13.0, 2 to 13.0, 3 to 12.0, 4 to 13.0), h.sosm2)
    }

    @Test
    fun `a bye contributes the player's own score and can be the dropped round`() {
        val h = HistoryHelper(listOf(
            listOf(Game(id = 1, table = 1, white = 1, black = 2, result = WHITE)),
            // round 2: player 1 gets the bye (stored as a win against the bye player)
            listOf(Game(id = 2, table = 0, white = 1, black = 0, result = WHITE)),
        )).apply {
            scoresFactory = { mapOf(1 to 12.0, 2 to 11.0) }
            scoresXFactory = scoresFactory
            missedRoundsSosFactory = { mapOf(1 to 9.0, 2 to 9.0) }
        }
        // player 1: [11 (opp 2), 9 (bye)]
        assertEquals(20.0, h.sos[1])
        assertEquals(11.0, h.sosm1[1])
    }
}
