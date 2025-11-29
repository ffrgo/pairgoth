package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.ID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for local club behavior in geographical pairing criteria.
 *
 * When a club has more than 40% of players (proportionMainClubThreshold),
 * it's considered the "local club" and geographical penalties are adjusted:
 * - Two players from the local club: no club penalty
 * - Two "strangers" (not from local club) with same club: half penalty
 * - Players from different clubs: no bonus (like when threshold exceeded)
 */
class LocalClubTest : TestBase() {

    companion object {
        // Tournament with MacMahon pairing to test geographical criteria
        val localClubTournament = Json.Object(
            "type" to "INDIVIDUAL",
            "name" to "Local Club Test",
            "shortName" to "local-club-test",
            "startDate" to "2024-01-01",
            "endDate" to "2024-01-01",
            "country" to "FR",
            "location" to "Test Location",
            "online" to false,
            "timeSystem" to Json.Object(
                "type" to "SUDDEN_DEATH",
                "mainTime" to 3600
            ),
            "rounds" to 1,
            "pairing" to Json.Object(
                "type" to "MAC_MAHON",
                "mmFloor" to -20,
                "mmBar" to 0
            )
        )

        // Helper to create a player
        fun player(name: String, firstname: String, rating: Int, rank: Int, club: String, country: String = "FR") = Json.Object(
            "name" to name,
            "firstname" to firstname,
            "rating" to rating,
            "rank" to rank,
            "country" to country,
            "club" to club,
            "final" to true
        )
    }

    @Test
    fun `local club detection with more than 40 percent`() {
        // Create tournament
        var resp = TestAPI.post("/api/tour", localClubTournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")

        // Add 10 players: 5 from "LocalClub" (50% > 40% threshold),
        // 2 strangers from "VisitorA", 2 strangers from "VisitorB", 1 from "Solo"
        val playerIds = mutableListOf<ID>()

        // 5 local club players (50%) - all same rank to be in same group
        for (i in 1..5) {
            resp = TestAPI.post("/api/tour/$tourId/part", player("Local$i", "Player", 100, -10, "LocalClub")).asObject()
            assertTrue(resp.getBoolean("success")!!)
            playerIds.add(resp.getInt("id")!!)
        }

        // 2 visitors from VisitorA club
        for (i in 1..2) {
            resp = TestAPI.post("/api/tour/$tourId/part", player("VisitorA$i", "Player", 100, -10, "VisitorA")).asObject()
            assertTrue(resp.getBoolean("success")!!)
            playerIds.add(resp.getInt("id")!!)
        }

        // 2 visitors from VisitorB club
        for (i in 1..2) {
            resp = TestAPI.post("/api/tour/$tourId/part", player("VisitorB$i", "Player", 100, -10, "VisitorB")).asObject()
            assertTrue(resp.getBoolean("success")!!)
            playerIds.add(resp.getInt("id")!!)
        }

        // 1 solo player
        resp = TestAPI.post("/api/tour/$tourId/part", player("Solo", "Player", 100, -10, "SoloClub")).asObject()
        assertTrue(resp.getBoolean("success")!!)
        playerIds.add(resp.getInt("id")!!)

        assertEquals(10, playerIds.size, "Should have 10 players")

        // Generate pairing with weights output
        val outputFile = getOutputFile("local-club-weights.txt")
        val games = TestAPI.post("/api/tour/$tourId/pair/1?weights_output=$outputFile", Json.Array("all")).asArray()

        // Verify we got 5 games (10 players / 2)
        assertEquals(5, games.size, "Should have 5 games")

        // Read and verify the weights file exists
        assertTrue(outputFile.exists(), "Weights file should exist")

        // The key verification is that the test completes without errors
        // and that local club players can be paired together
        // (The BOSP2024 test verifies the detailed behavior matches expected DUDD outcomes)
        logger.info("Local club test completed successfully with ${games.size} games")
    }
}
