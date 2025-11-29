package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.ext.MacMahon39
import org.jeudego.pairgoth.ext.OpenGotha
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.util.XmlUtils
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportTests: TestBase() {

    companion object {
        val maskIdRegex = Regex("(?<=\"id\" ?: ?)\\d+")
    }

    @Test
    fun `001 test imports`() {
        getTestResources("opengotha/tournamentfiles/")?.forEach { file ->
            logger.info("reading resource ${file.canonicalPath}")
            val resource = file.readText(StandardCharsets.UTF_8)
            //logger.info("post resource to api: $resource")
            val resp = TestAPI.post("/api/tour", resource)
            val id = resp.asObject().getInt("id")
            logger.info("read tournament id: $id")
            val tournament = TestAPI.get("/api/tour/$id").asObject()
            logger.info(tournament.toString().slice(0..50) + "...")
            val players = TestAPI.get("/api/tour/$id/part").asArray()
            logger.info(players.toString().slice(0..50) + "...")
            for (round in 1..tournament.getInt("rounds")!!) {
                val games = TestAPI.get("/api/tour/$id/res/1").asArray()
                logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")
            }
            val xml = TestAPI.getXml("/api/tour/$id")
            logger.info(xml.slice(0..50)+"...")
        }
    }

    @Test
    fun `002 test opengotha import export`() {
        // We import a tournament
        // Check that after exporting and reimporting we get the same pairgoth tournament object
        getTestResources("opengotha/tournamentfiles")?.forEach { file ->
            val resource = file.readText(StandardCharsets.UTF_8)
            val root_xml = XmlUtils.parse(resource)
            val tournament = OpenGotha.import(root_xml)
            // version which also compares players and games (not ready, need to reset ids in store)
            // val jsonTournament = tournament.toFullJson().toPrettyString()!!.replace(maskIdRegex, "0")
            val jsonTournament = tournament.toJson().toPrettyString()!!.replace(maskIdRegex, "0")

            val exported = OpenGotha.export(tournament)
            val tournament2 = OpenGotha.import(XmlUtils.parse(exported))
            // version which also compares players and games (not ready, need to reset ids in store)
            // val jsonTournament2 = tournament2.toFullJson().toPrettyString()!!.replace(maskIdRegex, "0")
            val jsonTournament2 = tournament2.toJson().toPrettyString()!!.replace(maskIdRegex, "0")

            assertEquals(jsonTournament, jsonTournament2)
        }
    }

    @Test
    fun `003 test macmahon39 import`() {
        getTestResources("macmahon39")?.forEach { file ->
            logger.info("===== Testing MacMahon 3.9 import: ${file.name} =====")
            val resource = file.readText(StandardCharsets.UTF_8)
            val root_xml = XmlUtils.parse(resource)

            // Verify format detection
            assertTrue(MacMahon39.isFormat(root_xml), "File should be detected as MacMahon 3.9 format")

            // Import tournament
            val tournament = MacMahon39.import(root_xml)

            // Verify basic tournament data
            logger.info("Tournament name: ${tournament.name}")
            logger.info("Number of rounds: ${tournament.rounds}")
            logger.info("Number of players: ${tournament.pairables.size}")

            assertEquals("Test MacMahon Tournament", tournament.name)
            assertEquals(3, tournament.rounds)
            assertEquals(4, tournament.pairables.size)

            // Verify players
            val players = tournament.pairables.values.toList()
            val alice = players.find { it.name == "Smith" }
            val bob = players.find { it.name == "Jones" }
            val carol = players.find { it.name == "White" }
            val david = players.find { it.name == "Brown" }

            assertTrue(alice != null, "Alice should exist")
            assertTrue(bob != null, "Bob should exist")
            assertTrue(carol != null, "Carol should exist")
            assertTrue(david != null, "David should exist")

            assertEquals(2, alice!!.rank) // 3d = rank 2
            assertEquals(1, bob!!.rank)   // 2d = rank 1
            assertEquals(0, carol!!.rank) // 1d = rank 0
            assertEquals(-1, david!!.rank) // 1k = rank -1

            // Carol is super bar member
            assertEquals(1, carol.mmsCorrection)

            // David skips round 2
            assertTrue(david.skip.contains(2), "David should skip round 2")

            // Verify games
            val round1Games = tournament.games(1).values.toList()
            val round2Games = tournament.games(2).values.toList()

            logger.info("Round 1 games: ${round1Games.size}")
            logger.info("Round 2 games: ${round2Games.size}")

            assertEquals(2, round1Games.size)
            assertEquals(2, round2Games.size) // 1 regular game + 1 bye

            // Test via API
            val resp = TestAPI.post("/api/tour", resource)
            val id = resp.asObject().getInt("id")
            logger.info("Imported tournament id: $id")

            val apiTournament = TestAPI.get("/api/tour/$id").asObject()
            assertEquals("Test MacMahon Tournament", apiTournament.getString("name"))
            assertEquals(3, apiTournament.getInt("rounds"))

            val apiPlayers = TestAPI.get("/api/tour/$id/part").asArray()
            assertEquals(4, apiPlayers.size)
        }
    }
}
