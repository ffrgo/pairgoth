package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.French
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * The French system, replayed on its author's own simulation: 42 players, 5 rounds, the favourite
 * always wins. Bonus groups per round: 10/10/10/12 (4,3,2,1), 14/14/14 (3,2,1), 21/21 (2,1), then
 * one group (+1 for all) and none.
 */
@TestMethodOrder(MethodName::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrenchSystemTest: TestBase() {

    private val players = 42
    private val rounds = 5

    // bonus of the player at starting place `place` (1 = strongest) for the given round
    private fun bonus(round: Int, place: Int): Int {
        val groups = rounds - round
        if (groups <= 0) return 0
        val size = players / groups
        return groups - minOf((place - 1) / size, groups - 1)
    }

    private fun tournament(): Pair<Int, Map<Int, Int>> {
        val tour = Json.Object(
            "type" to "INDIVIDUAL", "name" to "Fanfan", "shortName" to "fanfan",
            "startDate" to "2026-10-10", "endDate" to "2026-10-11",
            "country" to "FR", "location" to "Marseille", "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
            "rounds" to rounds,
            "pairing" to Json.Object(
                "type" to "FRENCH",
                "placement" to Json.Array("NBW", "MMS") // MMS = the pairing score, to read it back from the standings
            )
        )
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id")!!
        // registered in a scrambled order: the starting level is the rating, not the id
        val placeOfId = mutableMapOf<Int, Int>()
        for (place in (1..players).shuffled(java.util.Random(42))) {
            val rating = 2500 - 25 * (place - 1)
            val player = Json.Object(
                "name" to "P%02d".format(place), "firstname" to "F", "rating" to rating,
                "rank" to Math.floorDiv(rating - 2050, 100), "country" to "FR", "club" to "Mars", "final" to true)
            val id = TestAPI.post("/api/tour/$tourId/part", player).asObject().getInt("id")!!
            placeOfId[id] = place
        }
        return Pair(tourId, placeOfId)
    }

    @Test
    fun `100 the type carries swiss parameters, wins standings and a score-first draw`() {
        val french = Pairing.fromJson(Json.Object("type" to "FRENCH"), null)
        assertEquals(listOf(Criterion.NBW, Criterion.SOSW, Criterion.SOSOSW), french.placementParams.criteria)
        assertEquals(listOf(Criterion.MMS), french.pairingPlacementParams?.criteria)
        assertEquals(0.0, french.pairingParams.geo.avoidSameGeo)
        assertEquals(0, french.pairingParams.handicap.ceiling)
        // a settings update keeps the setting, an all-NONE list clears it, and a rebuild keeps the clearing
        val kept = Pairing.fromJson(Json.Object("placement" to Json.Array("NBW")), french)
        assertEquals(listOf(Criterion.MMS), kept.pairingPlacementParams?.criteria)
        val cleared = Pairing.fromJson(Json.Object("pairingPlacement" to Json.Array("NONE")), french)
        assertNull(cleared.pairingPlacementParams)
        assertNull(Pairing.fromJson(Json.Object("placement" to Json.Array("NBW")), cleared).pairingPlacementParams)
        assertEquals("FRENCH", (cleared as French).toJson().getString("type"))
    }

    @Test
    fun `200 replays the author's simulation`() {
        val (tourId, placeOfId) = tournament()
        for (round in 1..rounds) {
            // the pairing score before the round = wins so far + this round's bonus
            val standings = TestAPI.get("/api/tour/$tourId/standings/${round - 1}").asArray().map { it as Json.Object }
            assertEquals(players, standings.size)
            for (row in standings) {
                val place = placeOfId.getValue(row.getInt("id")!!)
                assertEquals(row.getDouble("NBW")!! + bonus(round, place), row.getDouble("MMS"), "round $round, player $place")
            }
            val games = TestAPI.post("/api/tour/$tourId/pair/$round", Json.Array("all")).asArray().map { it as Json.Object }
            assertEquals(players / 2, games.size)
            val pairs = games.map { Pair(placeOfId.getValue(it.getInt("w")!!), placeOfId.getValue(it.getInt("b")!!)) }
                .map { if (it.first < it.second) it else Pair(it.second, it.first) }.toSet()
            if (round == 1) {
                // split and slip inside each bonus group, by starting level
                val expected = ((1..5).map { Pair(it, it + 5) } + (11..15).map { Pair(it, it + 5) } +
                        (21..25).map { Pair(it, it + 5) } + (31..36).map { Pair(it, it + 6) }).toSet()
                assertEquals(expected, pairs)
            }
            // the favourite wins
            for (game in games) {
                val whiteStronger = placeOfId.getValue(game.getInt("w")!!) < placeOfId.getValue(game.getInt("b")!!)
                TestAPI.put("/api/tour/$tourId/res/$round", Json.parse("""{"id":${game.getInt("id")},"result":"${if (whiteStronger) "w" else "b"}"}""")).asObject()
            }
        }
        // one undefeated player, the strongest
        val final = TestAPI.get("/api/tour/$tourId/standings/$rounds").asArray().map { it as Json.Object }
        val undefeated = final.filter { it.getDouble("NBW") == rounds.toDouble() }.map { placeOfId.getValue(it.getInt("id")!!) }
        assertEquals(listOf(1), undefeated)
    }
}
