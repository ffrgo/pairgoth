package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.ext.MacMahon39
import org.jeudego.pairgoth.ext.OpenGotha
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.French
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.HandicapParams
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.PairingType
import org.jeudego.pairgoth.model.PlacementParams
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.Rules
import org.jeudego.pairgoth.model.StandardByoyomi
import org.jeudego.pairgoth.model.StandardTournament
import org.jeudego.pairgoth.model.Swiss
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.MemoryStore
import org.jeudego.pairgoth.util.XmlUtils
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDate
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

    private fun tournament(pairing: Pairing, vararg countries: String) = StandardTournament(
        id = 1, type = Tournament.Type.INDIVIDUAL, name = "Round trip", shortName = "roundtrip",
        startDate = LocalDate.of(2026, 11, 7), endDate = LocalDate.of(2026, 11, 8), director = "", country = "tr",
        location = "Bursa", online = false, timeSystem = StandardByoyomi(3600, 30, 1), rounds = 5,
        pairing = pairing, rules = Rules.JAPANESE, gobanSize = 13, komi = 6.5
    ).also {
        countries.forEachIndexed { i, country ->
            it.players[i + 1] = Player(id = i + 1, name = "Player$i", firstname = "P", rating = 1500, rank = -5, country = country, club = "Bur", final = true)
        }
    }

    // McMahon without handicap: the case the import used to take for a Swiss
    private fun evenMacMahon() = MacMahon().let {
        MacMahon(it.pairingParams.copy(handicap = HandicapParams(ceiling = 0)), it.placementParams)
    }

    // what a genuine OpenGotha file looks like: none of our own attributes
    private fun String.asForeignFile() = replace(Regex("""<GeneralParameterSet [^>]*>""")) { gps ->
        gps.value.replace(Regex(""" (country|rules|pairingType)="[^"]*""""), "")
    }

    @Test
    fun `004 opengotha round trip keeps what the format lacks`() {
        listOf(evenMacMahon(), French(), MacMahon(placementParams = PlacementParams(Criterion.NBW, Criterion.SOSW))).forEach { pairing ->
            val exported = OpenGotha.export(tournament(pairing, "FR", "FR", "TR"))
            assertTrue(!exported.contains("[<") && !exported.contains(">, <"), "criteria should not be written as a list")
            val imported = OpenGotha.import(XmlUtils.parse(exported))
            assertEquals(pairing.type, imported.pairing.type)
            assertEquals("tr", imported.country)
            assertEquals(Rules.JAPANESE, imported.rules)
            assertEquals(13, imported.gobanSize)
            assertEquals(6.5, imported.komi)
        }
    }

    @Test
    fun `005 foreign opengotha file`() {
        // system from the first placement criterion, as OpenGotha does; country from the players
        val mm = OpenGotha.import(XmlUtils.parse(OpenGotha.export(tournament(evenMacMahon(), "TR", "TR", "DE")).asForeignFile()))
        assertEquals(PairingType.MAC_MAHON, mm.pairing.type)
        assertEquals("tr", mm.country)
        assertEquals(6.5, mm.komi)
        assertEquals(13, mm.gobanSize)

        val swiss = OpenGotha.import(XmlUtils.parse(OpenGotha.export(tournament(French())).asForeignFile()))
        assertEquals(PairingType.SWISS, swiss.pairing.type)
        assertEquals("eu", swiss.country)
    }

    // Pairgo and rengo have no OpenGotha counterpart: each team is exported as one player, and the
    // file reads back as an individual tournament.
    @Test
    fun `006 rengo teams are exported as players`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", BasicTests.aRengoTournament).asObject().getInt("id")!!
        fun addPlayer(name: String, firstname: String, rating: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to firstname, "rating" to rating, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id")!!
        fun addTeam(name: String, vararg players: Int) = TestAPI.post("/api/tour/$tid/team",
            Json.Object("name" to name, "players" to Json.Array(*players.toTypedArray()))).asObject().getInt("id")!!
        // three teams: one of them gets the bye; two share their members' names and initials
        addTeam("Team one", addPlayer("Dupont", "Léa", 1900), addPlayer("Martin", "Rémi", 1800))
        addTeam("Team two", addPlayer("Dupont", "Luc", 1700), addPlayer("Martin", "Rose", 1600))
        addTeam("Team three", addPlayer("Le Gall", "Yann", 1500), addPlayer("Durand", "Zoé", 1400))
        TestAPI.post("/api/tour/$tid/pair/1", Json.parse("""["all"]"""))
        val games = TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("games")!!.map { it as Json.Object }
        val played = games.single { it.getInt("w") != 0 && it.getInt("b") != 0 }
        assertEquals(1, games.count { it.getInt("w") == 0 || it.getInt("b") == 0 }, "premise: one team has the bye")
        TestAPI.put("/api/tour/$tid/res/1", Json.Object("id" to played.getInt("id"), "result" to "b"))

        val exported = TestAPI.getXml("/api/tour/$tid")
        val imported = OpenGotha.import(XmlUtils.parse(exported))
        assertEquals(Tournament.Type.INDIVIDUAL, imported.type)
        assertEquals(
            setOf("Dupont L. - Martin R.", "Dupont L. - Martin R. (2)", "Le Gall Y. - Durand Z."),
            imported.players.values.map { "${it.name} ${it.firstname}" }.toSet())
        val round = imported.games(1).values
        assertEquals(1, round.count { it.black == 0 }, "the bye is kept")
        assertEquals(Game.Result.BLACK, round.single { it.black != 0 }.result)
    }

    // A team tournament saved by OpenGotha 3.51: 14 teams of 3, 5 rounds. OpenGotha stores players,
    // games and seats only; teams and matches are rebuilt. Board wins were counted apart, from the file.
    @Test
    fun `007 opengotha team tournament`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", getTestFile("opengotha/tournamentfiles/team-lgo-2020.xml").readText(StandardCharsets.UTF_8))
            .asObject().getInt("id")!!
        val tour = TestAPI.get("/api/tour/$tid").asObject()
        assertEquals("TEAM3", tour.getString("type"))
        assertEquals(listOf("MMS", "BDW", "BDW3", "BDW2", "BDW1", "RATING"), tour.getObject("pairing")!!.getArray("placement")!!.toList())
        val teams = TestAPI.get("/api/tour/$tid/team").asArray().map { it as Json.Object }
        assertEquals(14, teams.size)
        val names = teams.associate { it.getInt("id")!! to it.getString("name")!! }
        assertEquals(listOf("Zibaut Laurent", "Labbe David", "Tchernishoff Eric"),
            teams.single { it.getString("name") == "La Rochelle 1" }.getArray("names")!!.toList(), "members come in board order")

        val round1 = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        val matches = round1.getArray("games")!!.map { it as Json.Object }
        assertEquals(7, matches.size)
        assertEquals(21, round1.getArray("individualGames")!!.size)
        // tables 1 to 3 in the file: Lorient, white on the first board, loses it and wins the two others
        val first = matches.single { it.getInt("t") == 1 }
        assertEquals("Lorient" to "Rennes 2", names[first.getInt("w")] to names[first.getInt("b")])
        assertEquals("w", first.getString("r"))
        assertEquals(listOf(4, 5, 6, 7), matches.map { it.getInt("t")!! }.sorted().drop(3), "matches are numbered in table order")

        val standings = TestAPI.get("/api/tour/$tid/standings/5").asArray().map { it as Json.Object }
        fun boardWins(team: String) = standings.single { it.getString("name") == team }
            .let { row -> listOf("BDW", "BDW3", "BDW2", "BDW1").map { row.getDouble(it) } }
        assertEquals(listOf(12.0, 12.0, 7.0, 3.0), boardWins("Angers"))
        assertEquals(listOf(11.0, 11.0, 8.0, 3.0), boardWins("Lorient"))
        assertEquals(listOf(3.0, 3.0, 0.0, 0.0), boardWins("La Rochelle 2"))
    }

    // One of ours, through OpenGotha's format and back: teams, substitute, matches, boards and bye.
    @Test
    fun `008 team tournament round trip`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", Json.MutableObject(BasicTests.aTeamTournament).set("type", "TEAM3")).asObject().getInt("id")!!
        fun addPlayer(name: String, rating: Int, vararg skip: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to rating, "rank" to -1, "country" to "FR", "club" to "13Ma",
                "final" to true, "skip" to Json.Array(*skip.toTypedArray()))).asObject().getInt("id")!!
        fun addTeam(name: String, vararg players: Int) = TestAPI.post("/api/tour/$tid/team",
            Json.Object("name" to name, "players" to Json.Array(*players.toTypedArray()))).asObject().getInt("id")!!
        // the Alphas have a substitute: Add sits out round 1, Aaa round 2
        addTeam("Alphas", addPlayer("Aaa", 1900, 2), addPlayer("Abb", 1800), addPlayer("Acc", 1700), addPlayer("Add", 1600, 1))
        addTeam("Betas", addPlayer("Baa", 1850), addPlayer("Bbb", 1750), addPlayer("Bcc", 1650))
        addTeam("Gammas", addPlayer("Caa", 1500), addPlayer("Cbb", 1400), addPlayer("Ccc", 1300))
        TestAPI.put("/api/tour/$tid", Json.Object("pairing" to Json.Object("placement" to Json.Array("MMS", "BDW", "BDW2", "SOSOSM"))))
        TestAPI.post("/api/tour/$tid/pair/1", Json.parse("""["all"]"""))
        val boards = TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("individualGames")!!.map { it as Json.Object }
        assertEquals(3, boards.size, "premise: one match, and a team with the bye")
        listOf("w", "b", "w").forEachIndexed { i, result ->
            TestAPI.put("/api/tour/$tid/res/1", Json.Object("id" to boards[i].getInt("id"), "result" to result))
        }

        // what must survive, by names since ids are reissued
        fun picture(id: Int): String {
            val tour = TestAPI.get("/api/tour/$id").asObject()
            val players = TestAPI.get("/api/tour/$id/part").asArray().map { it as Json.Object }
            val teams = TestAPI.get("/api/tour/$id/team").asArray().map { it as Json.Object }
            val who = (players + teams).associate { it.getInt("id") to it.getString("name") } + (0 to "bye")
            val round = TestAPI.get("/api/tour/$id/pair/1").asObject()
            fun games(key: String) = round.getArray(key)!!.map { it as Json.Object }
                .map { "${it.getInt("t")}: ${who[it.getInt("w")]} - ${who[it.getInt("b")]} ${it.getString("r")}" }
            return listOf(
                tour.getString("type"), tour.getObject("pairing")!!.getArray("placement"),
                teams.map { "${it.getString("name")}: ${it.getArray("names")}" }.sorted(),
                players.map { "${it.getString("name")} skips ${it.getArray("skip") ?: "[]"}" }.sorted(),
                games("games").sorted(), games("individualGames")
            ).joinToString("\n")
        }
        val before = picture(tid)
        val copy = TestAPI.post("/api/tour", TestAPI.getXml("/api/tour/$tid")).asObject().getInt("id")!!
        assertEquals(before, picture(copy))
        assertTrue(before.contains("Add skips [1]") && before.contains(" - bye"), "premise: a substitute and a bye")
    }

    // OpenGotha matches criteria names exactly, and names some otherwise than we do (Rating,
    // SOSW-1...): it used to read ours as "no criterion", and its own failed our import.
    @Test
    fun `009 criteria travel under opengotha names`() {
        val swiss = Swiss(placementParams = PlacementParams(Criterion.NBW, Criterion.SOSWM1, Criterion.RATING, Criterion.EGFDC))
        val exported = OpenGotha.export(tournament(swiss))
        listOf("NBW", "SOSW-1", "Rating", "EGFDC", "NULL").forEach {
            assertTrue(exported.contains("""<PlacementCriterion name="$it""""), "$it is expected among the criteria")
        }
        assertTrue(exported.contains("""paiMaAdditionalPlacementCritSystem1="Rating""""))
        assertEquals(swiss.placementParams.criteria, OpenGotha.import(XmlUtils.parse(exported)).pairing.placementParams.criteria)
    }
}
