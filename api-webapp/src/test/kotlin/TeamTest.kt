package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.test.BasicTests.Companion.aPlayer
import org.jeudego.pairgoth.test.BasicTests.Companion.aTeamTournament
import org.jeudego.pairgoth.test.BasicTests.Companion.anotherPlayer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TeamTest {
    @Test
    fun `team tournament, MacMahon`() {
        var resp = TestAPI.post("/api/tour", aTeamTournament).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamTournamentID = resp.getInt("id")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", aPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", anotherPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        var arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asObject().getArray("pairables")
        assertEquals("[]", arr.toString(), "expecting an empty array")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Buffallos", "players":[$aTeamPlayerID, $anotherTeamPlayerID], "final":true }""")?.asObject() ?: fail("no null allowed here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamID = resp.getInt("id") ?: error("no null allowed here")
        resp = TestAPI.get("/api/tour/$aTeamTournamentID/team/$aTeamID").asObject()
        assertEquals("""{"id":$aTeamID,"name":"The Buffallos","players":[$aTeamPlayerID,$anotherTeamPlayerID]}""", resp.toString(), "expecting team description")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asObject().getArray("pairables")
        assertEquals("[$aTeamID]", arr.toString(), "expecting a singleton array")
        // nothing stops us in reusing players in different teams, at least for now...
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Billies", "players":[$aTeamPlayerID, $anotherTeamPlayerID], "final":true }""")?.asObject() ?: fail("no null here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamID = resp.getInt("id") ?: fail("no null here")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asObject().getArray("pairables")
        assertEquals("[$aTeamID,$anotherTeamID]", arr.toString(), "expecting two pairables")
        arr = TestAPI.post("/api/tour/$aTeamTournamentID/pair/1", Json.parse("""["all"]""")).asArray()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        // TODO check pairing
        // val expected = """"["id":1,"w":5,"b":6,"h":3,"r":"?"]"""
    }
}