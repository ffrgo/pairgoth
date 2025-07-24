package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.pairing.solver.Solver
import org.jeudego.pairgoth.test.PairingTests.Companion.compare_weights
import org.junit.jupiter.api.Test
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BOSP2024Test: TestBase() {

    @Test
    fun testDUDDProblem() {
        val tournament = Json.Companion.parse(
            getTestFile("opengotha/bosp2024/bosp2024.tour").readText(StandardCharsets.UTF_8)
        )!!.asObject()
        val resp = TestAPI.post("/api/tour", tournament).asObject()
        val tourId = resp.getInt("id")
        val outputFile = getOutputFile("bosp2024-weights.txt")
        TestAPI.post("/api/tour/$tourId/pair/3?legacy=true&weights_output=$outputFile", Json.Array("all")).asArray()

        // compare weights
        assertTrue(compare_weights(outputFile, getTestFile("opengotha/bosp2024/bosp2024_weights_R3.txt")), "Not matching opengotha weights for BOSP test")
        TestAPI.delete("/api/tour/$tourId/pair/3", Json.Array("all"))

        val games = TestAPI.post("/api/tour/$tourId/pair/3", Json.Array("all")).asArray()
        // Aksut Husrev is ID 18
        val solved = games.map { it as Json.Object }.firstOrNull { game ->
            // build the two-elements set of players ids
            val players =
                game.entries.filter { (k, v) -> k == "b" || k == "w" }.map { (k, v) -> (v as Number).toInt() }.toSet()
            // keep game with Aksut Husrev
            players.contains(18)
        }

        assertNotNull(solved)

        // if the bug is still here, the opponent is Suleymanoglu Serap, id 24
        val black = solved.getInt("b")!!
        val white = solved.getInt("w")!!
        val opponent = if (black == 18) white else black
        assertNotEquals(24, opponent)

    }
}
