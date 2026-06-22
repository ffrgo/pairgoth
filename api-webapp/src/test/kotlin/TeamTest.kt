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

    // A team match result must reflect which TEAMS won their boards, not which stone colours won.
    // Boards alternate colours, so counting white stones turned a 2-0 sweep into a "draw".
    @Test
    fun `team result follows team wins, not stone colours`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        fun addPlayer(name: String, rating: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to rating, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id") ?: fail("no player id")
        fun addTeam(name: String, p1: Int, p2: Int) = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"$name", "players":[$p1,$p2], "final":true }""")?.asObject() ?: fail("no null"))
            .asObject().getInt("id") ?: fail("no team id")
        addTeam("Alphas", addPlayer("Alpha", 1900), addPlayer("Beta", 1800))
        addTeam("Gammas", addPlayer("Gamma", 1700), addPlayer("Delta", 1600))
        // pair round 1 -> one team match over two boards (colours alternate between boards)
        TestAPI.post("/api/tour/$tid/pair/1", Json.parse("""["all"]"""))
        val pairing = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        val teamGame = pairing.getArray("games")?.getJson(0)?.asObject() ?: fail("no team game")
        val whiteTeamId = teamGame.getInt("w") ?: fail("no white team")
        val whiteTeamPlayers = TestAPI.get("/api/tour/$tid/team/$whiteTeamId").asObject()
            .getArray("players")!!.map { (it as Number).toInt() }.toSet()
        // let the white team win EVERY board, whatever stones it holds on each
        pairing.getArray("individualGames")!!.forEach { obj ->
            val ig = obj as Json.Object
            val whiteTeamHoldsWhite = whiteTeamPlayers.contains(ig.getInt("w"))
            TestAPI.put("/api/tour/$tid/res/1",
                Json.parse("""{"id":${ig.getInt("id")},"result":"${if (whiteTeamHoldsWhite) "w" else "b"}"}"""))
        }
        val teamResult = TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("games")!!
            .map { it as Json.Object }.first { it.getInt("id") == teamGame.getInt("id") }.getString("r")
        assertEquals("w", teamResult, "the white team swept both boards — the match must be a white win, not a draw")
    }
}