package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toFullJson
import org.jeudego.pairgoth.store.MemoryStore
import org.jeudego.pairgoth.test.BasicTests.Companion.aPlayer
import org.jeudego.pairgoth.test.BasicTests.Companion.aRengoTournament
import org.jeudego.pairgoth.test.BasicTests.Companion.aTeamTournament
import org.jeudego.pairgoth.test.BasicTests.Companion.anotherPlayer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TeamTest {
    @Test
    fun `team tournament, MacMahon`() {
        var resp = TestAPI.post("/api/tour", aRengoTournament).asObject()
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
        assertEquals("""{"id":$aTeamID,"name":"The Buffallos","players":[$aTeamPlayerID,$anotherTeamPlayerID],"rating":1750,"rank":-3,"country":"FR","names":["Burma Nestor","Poirot Hercule"],"ranks":[-5,-1]}""", resp.toString(), "expecting team description")
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

    // Operator reports (LRA 2026, tournaments 280/231): deleting a player who belongs to a team left a
    // dangling member id in the team. The team silently shrank in memory (fewer individual boards, lost
    // results), and on the next reload teamFromJson() rejected the missing id with "invalid player id",
    // making the whole tournament unloadable ("tout est HS").
    @Test
    fun `deleting a team member must not corrupt the tournament`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        val p1 = TestAPI.post("/api/tour/$tid/part", aPlayer).asObject().getInt("id") ?: fail("no player id")
        val p2 = TestAPI.post("/api/tour/$tid/part", anotherPlayer).asObject().getInt("id") ?: fail("no player id")
        TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"The Buffallos", "players":[$p1, $p2], "final":true }""")?.asObject() ?: fail("no null here"))
        // delete a player belonging to a (still unpaired) team
        try { TestAPI.delete("/api/tour/$tid/part/$p1", Json.Object()) }
        catch (e: Throwable) { /* refusing the deletion is an acceptable fix; corrupting the tournament is not */ }
        // the tournament must still load: a serialize → reload round-trip is the exact path that crashes
        // for the operator (FileStore persists toFullJson() and reparses it via Tournament.fromJson())
        val tournament = MemoryStore.getTournament(tid) ?: fail("tournament vanished")
        Tournament.fromJson(tournament.toFullJson())
    }
}