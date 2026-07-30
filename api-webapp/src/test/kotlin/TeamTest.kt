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
        // a player belongs to at most one team: reusing them must be refused
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Billies", "players":[$aTeamPlayerID, $anotherTeamPlayerID], "final":true }""")?.asObject() ?: fail("no null here")).asObject()
        assertTrue(resp.getBoolean("success") == false, "expecting failure")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", Json.Object("name" to "Maigret", "firstname" to "Jules", "rating" to 1500, "rank" to -6, "country" to "FR", "club" to "75Op", "final" to true)).asObject()
        val aThirdPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", Json.Object("name" to "Holmes", "firstname" to "Sherlock", "rating" to 1700, "rank" to -4, "country" to "GB", "club" to "Lond", "final" to true)).asObject()
        val aFourthPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Billies", "players":[$aThirdPlayerID, $aFourthPlayerID], "final":true }""")?.asObject() ?: fail("no null here")).asObject()
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

    // Operator report (EGC 2026 Nations Cup): a create-team response lost on venue wifi → the operator
    // resubmitted the same selection 10s later → two identical teams sharing all seven players, both
    // pairable. The server now enforces "a player belongs to at most one team" on team POST and PUT.
    @Test
    fun `a player must not land in two teams`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        fun addPlayer(name: String) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to 1800, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id") ?: fail("no player id")
        val p1 = addPlayer("Aaa")
        val p2 = addPlayer("Bbb")
        val p3 = addPlayer("Ccc")
        val p4 = addPlayer("Ddd")
        TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Duo", "players":[$p1,$p2] }""")?.asObject() ?: fail("no null here"))
        // the exact prod scenario: identical resubmission
        val dup = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Duo", "players":[$p1,$p2] }""")?.asObject() ?: fail("no null here")).asObject()
        assertTrue(dup.getBoolean("success") == false, "duplicate team must be refused")
        assertTrue(dup.getString("error")!!.contains("already in team"), "expecting the guard's message")
        val teamB = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Other", "players":[$p3,$p4] }""")?.asObject() ?: fail("no null here"))
            .asObject().getInt("id") ?: fail("no team id")
        // a PUT must not steal another team's member...
        assertTrue(TestAPI.put("/api/tour/$tid/team/$teamB", Json.parse("""{ "players":[$p3,$p1] }"""))
            .asObject().getBoolean("success") == false, "member theft must be refused")
        // ...but reshuffling its own members stays legal (join/leave/rename path)
        assertTrue(TestAPI.put("/api/tour/$tid/team/$teamB", Json.parse("""{ "players":[$p4] }"""))
            .asObject().getBoolean("success") == true)
    }

    // A tournament-form edit rebuilds the TeamTournament but used to transplant the old inner-class
    // Team objects, still bound to the pre-edit instance: player edits made after the edit (e.g.
    // benching a substitute) were invisible to Team.canPlay, so a 3-player TEAM2 stayed unpairable
    // while the roster showed the skip. Teams must be re-parented onto the new instance.
    @Test
    fun `player edits after a tournament edit must be visible to teams`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        fun addPlayer(name: String, rating: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to rating, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id") ?: fail("no player id")
        val p1 = addPlayer("Aaa", 1800)
        val p2 = addPlayer("Bbb", 1790)
        val sub = addPlayer("Ccc", 1780)
        val team = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Trio", "players":[$p1,$p2,$sub], "final":true }""")?.asObject() ?: fail("no null here"))
            .asObject().getInt("id") ?: fail("no team id")
        // 3 active players on a 2-board team: not pairable yet
        assertEquals("[]", TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("pairables").toString())
        // edit the tournament form (rounds 2 → 4): rebuilds the tournament instance
        TestAPI.put("/api/tour/$tid", Json.Object("rounds" to 4)).asObject().also { assertTrue(it.getBoolean("success")!!) }
        // bench the substitute for round 1
        TestAPI.put("/api/tour/$tid/part/$sub", Json.Object("skip" to Json.Array(1))).asObject().also { assertTrue(it.getBoolean("success")!!) }
        // the team must now be pairable: exactly 2 active players
        assertEquals("[$team]", TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("pairables").toString())
    }

    // Team rating/rank are live means over the current members: a member's rating update or a
    // roster change must be reflected immediately (they feed pairing via effectiveRank), instead
    // of staying frozen at the value computed when the team was created.
    @Test
    fun `team rating follows member ratings and roster changes`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        fun addPlayer(name: String, rating: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to rating, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id") ?: fail("no player id")
        val p1 = addPlayer("Aaa", 1900)
        val p2 = addPlayer("Bbb", 1700)
        val team = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Duo", "players":[$p1,$p2], "final":true }""")?.asObject() ?: fail("no null here"))
            .asObject().getInt("id") ?: fail("no team id")
        fun teamRating() = TestAPI.get("/api/tour/$tid/team/$team").asObject().getInt("rating")
        assertEquals(1800, teamRating(), "team rating must be the members' mean")
        // a ratings refresh reaches the team
        TestAPI.put("/api/tour/$tid/part/$p1", Json.Object("rating" to 2100))
        assertEquals(1900, teamRating(), "a member's rating update must reach the team mean")
        // a roster change reaches the team
        val p3 = addPlayer("Ccc", 2100)
        TestAPI.put("/api/tour/$tid/team/$team", Json.parse("""{ "players":[$p1,$p3] }"""))
        assertEquals(2100, teamRating(), "a roster change must reach the team mean")
    }

    // Team final is live too: a team built around a preliminary registration becomes pairable
    // the moment that member is finalized (it used to stay frozen non-final until a team PUT).
    @Test
    fun `team final follows member finalization`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        fun addPlayer(name: String, final: Boolean) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to 1800, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to final)).asObject().getInt("id") ?: fail("no player id")
        val p1 = addPlayer("Aaa", true)
        val p2 = addPlayer("Bbb", false)
        val team = TestAPI.post("/api/tour/$tid/team",
            Json.parse("""{ "name":"Duo", "players":[$p1,$p2] }""")?.asObject() ?: fail("no null here"))
            .asObject().getInt("id") ?: fail("no team id")
        fun pairables() = TestAPI.get("/api/tour/$tid/pair/1").asObject().getArray("pairables").toString()
        assertEquals("[]", pairables(), "a team with a preliminary member must not be pairable")
        TestAPI.put("/api/tour/$tid/part/$p2", Json.Object("final" to true))
        assertEquals("[$team]", pairables(), "finalizing the member must make the team pairable")
    }

    // Builds a TEAM2 with two full teams, pairs round 1 (one match over two boards), and lets the
    // board-0 white team win EVERY board (whatever stones it holds). Returns (tournamentId, teamGame).
    private fun sweptTeamMatch(): Pair<Int, Json.Object> {
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
        TestAPI.post("/api/tour/$tid/pair/1", Json.parse("""["all"]"""))
        val pairing = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        val teamGame = pairing.getArray("games")?.getJson(0)?.asObject() ?: fail("no team game")
        val whiteTeamPlayers = TestAPI.get("/api/tour/$tid/team/${teamGame.getInt("w")}").asObject()
            .getArray("players")!!.map { (it as Number).toInt() }.toSet()
        pairing.getArray("individualGames")!!.forEach { obj ->
            val ig = obj as Json.Object
            val whiteTeamHoldsWhite = whiteTeamPlayers.contains(ig.getInt("w"))
            TestAPI.put("/api/tour/$tid/res/1",
                Json.parse("""{"id":${ig.getInt("id")},"result":"${if (whiteTeamHoldsWhite) "w" else "b"}"}"""))
        }
        return tid to teamGame
    }

    private fun teamGame(tid: Int, id: Int?) = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        .getArray("games")!!.map { it as Json.Object }.first { it.getInt("id") == id }
    private fun boards(tid: Int) = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        .getArray("individualGames")!!.map { it as Json.Object }

    // A team match result must reflect which TEAMS won their boards, not which stone colours won.
    // Boards alternate colours, so counting white stones turned a 2-0 sweep into a "draw".
    @Test
    fun `team result follows team wins, not stone colours`() {
        val (tid, tg) = sweptTeamMatch()
        assertEquals("w", teamGame(tid, tg.getInt("id")).getString("r"),
            "the white team swept both boards — the match must be a white win, not a draw")
    }

    // A table move keeps the same two teams: results must survive, boards must follow to the new table.
    @Test
    fun `editing a team game's table keeps its results`() {
        val (tid, tg) = sweptTeamMatch()
        val (w, b, id) = Triple(tg.getInt("w"), tg.getInt("b"), tg.getInt("id"))
        TestAPI.put("/api/tour/$tid/pair/1", Json.parse("""{"id":$id,"w":$w,"b":$b,"h":"0","t":"7"}"""))
        assertEquals("w", teamGame(tid, id).getString("r"), "team result must survive a table move")
        assertTrue(boards(tid).all { it.getString("r") != "?" }, "individual results must survive a table move")
        assertTrue(boards(tid).all { it.getInt("t") == 7 }, "boards must follow the team game to its new table")
    }

    // Swapping the team colours (same two teams) cascades to all boards: the winner is unchanged,
    // it just plays the other colour now — and the entered results survive.
    @Test
    fun `swapping a team game's colours flips the result but keeps the winner and results`() {
        val (tid, tg) = sweptTeamMatch()
        val (w, b, id) = Triple(tg.getInt("w"), tg.getInt("b"), tg.getInt("id"))
        TestAPI.put("/api/tour/$tid/pair/1", Json.parse("""{"id":$id,"w":$b,"b":$w,"h":"0"}"""))
        val after = teamGame(tid, id)
        assertEquals(b, after.getInt("w"), "the team game colours must be swapped")
        assertEquals("b", after.getString("r"), "the winning team now plays black, so the match reads as a black win")
        assertTrue(boards(tid).all { it.getString("r") != "?" }, "individual results must survive a colour swap")
    }

    // Per-board override from the result screen: swapping one board's colours flips that board's
    // colours/result (keeping its winner) and must NOT change the match result.
    @Test
    fun `swapping one board's colours leaves the match result unchanged`() {
        val (tid, tg) = sweptTeamMatch()
        val board = boards(tid).first()
        val bw = board.getInt("w"); val bb = board.getInt("b"); val bres = board.getString("r")
        TestAPI.put("/api/tour/$tid/res/1", Json.parse("""{"id":${board.getInt("id")},"swap":true}"""))
        val after = boards(tid).first { it.getInt("id") == board.getInt("id") }
        assertEquals(bb, after.getInt("w"), "the board colours must be swapped")
        assertEquals(bw, after.getInt("b"))
        assertEquals(if (bres == "w") "b" else if (bres == "b") "w" else bres, after.getString("r"),
            "the board result must flip so its winner is unchanged")
        assertEquals("w", teamGame(tid, tg.getInt("id")).getString("r"),
            "a per-board colour override must not change the match result")
    }

    // Nations Cup, EGC 2026: putting SCOREX first in the placement criteria of a *team* MacMahon
    // white-paged the whole tournament page — the standings view inserts an MMS column whenever
    // SCOREX comes first, but standings rows only carried an MMS value for INDIVIDUAL tournaments.
    @Test
    fun `team MacMahon standings carry MMS`() {
        MemoryStore.reset()
        val tid = TestAPI.post("/api/tour", aTeamTournament).asObject().getInt("id") ?: fail("no tournament id")
        TestAPI.put("/api/tour/$tid", Json.Object(
            "pairing" to Json.Object("placement" to Json.Array("SCOREX", "DC", "SOSM", "SOSOSM"))))
        fun addPlayer(name: String, rating: Int) = TestAPI.post("/api/tour/$tid/part",
            Json.Object("name" to name, "firstname" to "X", "rating" to rating, "rank" to -1,
                "country" to "FR", "club" to "13Ma", "final" to true)).asObject().getInt("id") ?: fail("no player id")
        val teams = listOf("Ducks" to listOf(addPlayer("Aaa", 1900), addPlayer("Bbb", 1700)),
                           "Drakes" to listOf(addPlayer("Ccc", 1850), addPlayer("Ddd", 1750)))
        teams.forEach { (name, players) ->
            TestAPI.post("/api/tour/$tid/team",
                Json.parse("""{ "name":"$name", "players":${players}, "final":true }""")?.asObject() ?: fail("no null here"))
        }
        TestAPI.post("/api/tour/$tid/pair/1", Json.Array("all"))
        val standings = TestAPI.get("/api/tour/$tid/standings/1").asArray()
        assertEquals(2, standings.size, "one standings row per team")
        standings.forEach { row ->
            assertTrue((row as Json.Object).containsKey("MMS"), "team standings rows must carry MMS: $row")
        }
    }
}