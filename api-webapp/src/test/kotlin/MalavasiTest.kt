package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.pairing.solver.BaseSolver
import org.jeudego.pairgoth.test.PairingTests.Companion.compare_weights
import org.junit.jupiter.api.Test
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MalavasiTest: TestBase() {

    @Test
    fun testMalavasi() {
        val tournament = Json.Companion.parse(
            getTestFile("opengotha/malavasi/malavasi.geobug.tour").readText(StandardCharsets.UTF_8)
        )!!.asObject()
        val resp = TestAPI.post("/api/tour", tournament).asObject()
        val tourId = resp.getInt("id")
        BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("malavasi-weights.txt")))
        val games = TestAPI.post("/api/tour/$tourId/pair/2", Json.Array("all")).asArray()
        // Oceane is ID 548, Valentine 549
        val buggy = games.map { it as Json.Object }.filter { game ->
            // build the two-elements set of players ids
            val players = game.entries.filter { (k, v) -> k == "b" || k == "w" }.map { (k, v) -> (v as Number).toInt() }.toSet()
            // keep games with Oceane or Valentine
            players.contains(548) || players.contains(549)
        }

        // if the bug is still here, buggy contains a single element
        assertEquals(2, buggy.size)

        // compare weights
        assertTrue(compare_weights(getOutputFile("malavasi-weights.txt"), getTestFile("opengotha/malavasi/malavasi_weights_R2.txt")), "Not matching opengotha weights for Malavasi test")
    }
}
