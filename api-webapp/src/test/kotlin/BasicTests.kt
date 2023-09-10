package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.model.ID
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@TestMethodOrder(MethodName::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicTests: TestBase() {

    val aTournament = Json.Object(
        "type" to "INDIVIDUAL",
        "name" to "Mon Tournoi",
        "shortName" to "mon-tournoi",
        "startDate" to "2023-05-10",
        "endDate" to "2023-05-12",
        "country" to "FR",
        "location" to "Marseille",
        "online" to false,
        "timeSystem" to Json.Object(
            "type" to "FISCHER",
            "mainTime" to 1200,
            "increment" to 10
        ),
        "rounds" to 2,
        "pairing" to Json.Object(
            "type" to "SWISS",
            "method" to "SPLIT_AND_SLIP"
        )
    )

    val aSimpleSwissTournament = Json.Object(
        "type" to "INDIVIDUAL",
        "name" to "Simple Swiss",
        "shortName" to "simple-swiss",
        "startDate" to "2023-05-10",
        "endDate" to "2023-05-12",
        "country" to "FR",
        "location" to "Grenoble",
        "online" to false,
        "timeSystem" to Json.Object(
            "type" to "FISCHER",
            "mainTime" to 1800,
            "increment" to 15
        ),
        "rounds" to 4,
        "pairing" to Json.Object(
            "type" to "SWISS",
            "method" to "SPLIT_AND_SLIP"
        )
    )

    val aTeamTournament = Json.Object(
        "type" to "TEAM2",
        "name" to "Mon Tournoi par équipes",
        "shortName" to "mon-tournoi-par-equipes",
        "startDate" to "2023-05-20",
        "endDate" to "2023-05-23",
        "country" to "FR",
        "location" to "Marseille",
        "online" to true,
        "timeSystem" to Json.Object(
            "type" to "FISCHER",
            "mainTime" to 1200,
            "increment" to 10
        ),
        "rounds" to 2,
        "pairing" to Json.Object(
            "type" to "MAC_MAHON"
        )
    )

    val aPlayer = Json.Object(
        "name" to "Burma",
        "firstname" to "Nestor",
        "rating" to 1600,
        "rank" to -5,
        "country" to "FR",
        "club" to "13Ma"
    )

    val anotherPlayer = Json.Object(
        "name" to "Poirot",
        "firstname" to "Hercule",
        "rating" to 1700,
        "rank" to -1,
        "country" to "FR",
        "club" to "75Op"
    )

    var aTournamentID: ID? = null
    var aTeamTournamentID: ID? = null
    var aPlayerID: ID? = null
    var anotherPlayerID: ID? = null
    var aTournamentGameID: ID? = null
    
    @Test
    fun `001 create tournament`() {
        val resp = TestAPI.post("/api/tour", aTournament).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aTournamentID = resp.getInt("id")
        assertNotNull(aTournamentID)
    }

    @Test
    fun `002 get tournament`() {
        val resp = TestAPI.get("/api/tour/$aTournamentID").asObject()
        assertEquals(aTournamentID, resp.getInt("id"), "First tournament should have id #$aTournamentID")
        // filter out "id", and also "komi", "rules" and "gobanSize" which were provided by default
        // also filter out "pairing", which is filled by all default values
        val cmp = Json.Object(*resp.entries.filter { it.key !in listOf("id", "komi", "rules", "gobanSize", "pairing") }.map { Pair(it.key, it.value) }.toTypedArray())
        val expected = aTournament.entries.filter { it.key != "pairing" }.map { Pair(it.key, it.value) }.toMap().toJsonObject()
        assertEquals(expected.toString(), cmp.toString(), "tournament differs")
    }

    @Test
    fun `003 register user`() {
        val resp = TestAPI.post("/api/tour/$aTournamentID/part", aPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aPlayerID = resp.getInt("id")
        val players = TestAPI.get("/api/tour/$aTournamentID/part").asArray()
        val player = players[0] as Json.Object
        assertEquals(aPlayerID, player.getInt("id"), "First player should have id #$aPlayerID")
        // filter out "id"
        val cmp = Json.Object(*player.entries.filter { it.key != "id" }.map { Pair(it.key, it.value) }.toTypedArray())
        assertEquals(aPlayer.toString(), cmp.toString(), "player differs")
    }

    @Test
    fun `004 modify user`() {
        // remove player aPlayer from round #2
        val resp = TestAPI.put("/api/tour/$aTournamentID/part/$aPlayerID", Json.Object("skip" to Json.Array(2))).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val player = TestAPI.get("/api/tour/$aTournamentID/part/$aPlayerID").asObject()
        assertEquals("[2]", player.getArray("skip").toString(), "First player should skip round #2")
    }

    @Test
    fun `005 pair`() {
        val resp = TestAPI.post("/api/tour/$aTournamentID/part", anotherPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        anotherPlayerID = resp.getInt("id")
        var games = TestAPI.post("/api/tour/$aTournamentID/pair/1", Json.Array("all")).asArray()
        aTournamentGameID = (games[0] as Json.Object).getInt("id")
        val possibleResults = setOf(
            """[{"id":$aTournamentGameID,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"?"}]""",
            """[{"id":$aTournamentGameID,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"?"}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "pairing differs")
        games = TestAPI.get("/api/tour/$aTournamentID/res/1").asArray()
        assertTrue(possibleResults.contains(games.toString()), "results differs")
        val empty = TestAPI.get("/api/tour/$aTournamentID/pair/1").asArray()
        assertEquals("[]", empty.toString(), "no more pairables for round 1")
    }

    @Test
    fun `006 result`() {
        val resp = TestAPI.put("/api/tour/$aTournamentID/res/1", Json.parse("""{"id":$aTournamentGameID,"result":"b"}""")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val games = TestAPI.get("/api/tour/$aTournamentID/res/1")
        val possibleResults = setOf(
            """[{"id":$aTournamentGameID,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"b"}]""",
            """[{"id":$aTournamentGameID,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"b"}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "results differ")
    }

    @Test
    fun `007 team tournament, MacMahon`() {
        var resp = TestAPI.post("/api/tour", aTeamTournament).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aTeamTournamentID = resp.getInt("id")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", aPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", anotherPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        var arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[]", arr.toString(), "expecting an empty array")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Buffallos", "players":[$aTeamPlayerID, $anotherTeamPlayerID] }""")?.asObject() ?: fail("no null allowed here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamID = resp.getInt("id") ?: error("no null allowed here")
        resp = TestAPI.get("/api/tour/$aTeamTournamentID/team/$aTeamID").asObject()
        assertEquals("""{"id":$aTeamID,"name":"The Buffallos","players":[$aTeamPlayerID,$anotherTeamPlayerID]}""", resp.toString(), "expecting team description")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[$aTeamID]", arr.toString(), "expecting a singleton array")
        // nothing stops us in reusing players in different teams, at least for now...
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Billies", "players":[$aTeamPlayerID, $anotherTeamPlayerID] }""")?.asObject() ?: fail("no null here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamID = resp.getInt("id") ?: fail("no null here")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[$aTeamID,$anotherTeamID]", arr.toString(), "expecting two pairables")
        arr = TestAPI.post("/api/tour/$aTeamTournamentID/pair/1", Json.parse("""["all"]""")).asArray()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        // TODO check pairing
        // val expected = """"["id":1,"w":5,"b":6,"h":3,"r":"?"]"""
    }

    @Test
    fun `008 simple swiss tournament`() {
        var file = getTestFile("opengotha/simpleswiss.xml")
        println(file)

        val resource = file.readText(StandardCharsets.UTF_8)
        val resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")
        for (round in 1..tournament.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$id/res/1").asArray()
            logger.info("games for round $round: {}", games.toString())
        }

    }
}
