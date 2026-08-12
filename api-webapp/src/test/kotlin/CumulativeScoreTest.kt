package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Game.Result.JIGO
import org.jeudego.pairgoth.model.Game.Result.WHITE
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The cumulative score (CUSS) is the sum, over every round, of the score standing at the end of
 * that round — it rewards winning early. The accumulator used to add the running total to itself
 * at each step, doubling every earlier round from the third one on.
 */
class CumulativeScoreTest : TestBase() {

    private fun helper(history: List<List<Game>>) = HistoryHelper(history).apply {
        scoresFactory = { mapOf() }
        scoresXFactory = scoresFactory
        missedRoundsSosFactory = { mapOf() }
    }

    @Test
    fun `winning early is worth more than winning late`() {
        // 3 rounds; player 1 wins the first two, player 2 wins the last one
        val h = helper(listOf(
            listOf(Game(id = 1, table = 1, white = 1, black = 2, result = WHITE)),
            listOf(Game(id = 2, table = 1, white = 1, black = 2, result = WHITE)),
            listOf(Game(id = 3, table = 1, white = 2, black = 1, result = WHITE)),
        ))
        // player 1 stands at 1, 2, 2 -> 5;  player 2 at 0, 0, 1 -> 1
        assertEquals(5.0, h.cumScore[1])
        assertEquals(1.0, h.cumScore[2])
    }

    @Test
    fun `an empty history has no cumulative score`() {
        assertEquals(mapOf(), helper(listOf()).cumScore)
    }

    @Test
    fun `a jigo adds half a point to the running total`() {
        val h = helper(listOf(
            listOf(Game(id = 1, table = 1, white = 1, black = 2, result = JIGO)),
            listOf(Game(id = 2, table = 1, white = 1, black = 2, result = WHITE)),
        ))
        // player 1 stands at ½ then 1½ -> 2
        assertEquals(2.0, h.cumScore[1])
        assertEquals(1.0, h.cumScore[2])
    }
}
