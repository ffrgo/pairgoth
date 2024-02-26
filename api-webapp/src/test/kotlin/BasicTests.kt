package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.model.ID
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestMethodOrder(MethodName::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicTests: TestBase() {

    companion object {

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
            "rating" to -500,
            "rank" to -5,
            "country" to "FR",
            "club" to "13Ma",
            "final" to true
        )

        val anotherPlayer = Json.Object(
            "name" to "Poirot",
            "firstname" to "Hercule",
            "rating" to -100,
            "rank" to -1,
            "country" to "FR",
            "club" to "75Op"
        )

        val aMMTournament = Json.Object(
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
                "type" to "MAC_MAHON",
                "handicap" to Json.Object(
                    "correction" to 1
                )
            )
        )
    }


    var aTournamentID: ID? = null
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
            """[{"id":$aTournamentGameID,"t":1,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"?","dd":0}]""",
            """[{"id":$aTournamentGameID,"t":1,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"?","dd":0}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "pairing differs")
        games = TestAPI.get("/api/tour/$aTournamentID/res/1").asArray()!!
        assertTrue(possibleResults.contains(games.toString()), "results differs")
        val empty = TestAPI.get("/api/tour/$aTournamentID/pair/1").asObject().getArray("pairables")
        assertEquals("[]", empty.toString(), "no more pairables for round 1")
    }

    @Test
    fun `006 result`() {
        val resp = TestAPI.put("/api/tour/$aTournamentID/res/1", Json.parse("""{"id":$aTournamentGameID,"result":"b"}""")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val games = TestAPI.get("/api/tour/$aTournamentID/res/1")
        val possibleResults = setOf(
            """[{"id":$aTournamentGameID,"t":1,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"b","dd":0}]""",
            """[{"id":$aTournamentGameID,"t":1,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"b","dd":0}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "results differ")
    }
}
