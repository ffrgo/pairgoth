package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.TokenHandler
import org.jeudego.pairgoth.model.CanadianByoyomi
import org.jeudego.pairgoth.model.FischerTime
import org.jeudego.pairgoth.model.StandardByoyomi
import org.jeudego.pairgoth.model.SuddenDeath
import org.jeudego.pairgoth.model.TimeSystem
import org.jeudego.pairgoth.model.adjustedTime
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.server.WebappManager
import org.jeudego.pairgoth.test.BasicTests.Companion.aMMTournament
import org.jeudego.pairgoth.test.BasicTests.Companion.aPlayer
import org.jeudego.pairgoth.test.BasicTests.Companion.aTournament
import org.jeudego.pairgoth.test.BasicTests.Companion.anotherPlayer
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.web.sharedSecret
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import javax.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression tests for fixes that shipped without one (see CHANGELOG 0.26), plus API edge cases.
 */
class RegressionTests: TestBase() {

    private fun createTournament(payload: Json.Object): Int =
        TestAPI.post("/api/tour", payload).asObject().getInt("id") ?: fail("no tournament id")

    private fun addPlayer(tid: Int, player: Json.Object): Int =
        TestAPI.post("/api/tour/$tid/part", player).asObject().getInt("id") ?: fail("no player id")

    private fun withPairing(base: Json.Object, pairing: Json.Object) =
        Json.MutableObject(base).also { it["pairing"] = pairing }

    // --- JSON output ---

    @Test
    fun `strings needing JSON escapes survive an API round-trip`() {
        // ApiServlet writes every payload through JsonIO's Writer adapter
        val name = """Le "Grand" Prix \ d'été"""
        val tid = createTournament(Json.MutableObject(aTournament).also { it["name"] = name })
        val tournament = TestAPI.get("/api/tour/$tid").asObject()
        assertEquals(name, tournament.getString("name"))
    }

    // --- EGF export (0.26: .h9 extension for even games, HA header keeps the real correction) ---

    private fun egfExport(tournament: Json.Object): Pair<String, String> {
        val tid = createTournament(tournament)
        addPlayer(tid, aPlayer)
        addPlayer(tid, Json.MutableObject(anotherPlayer).also { it["final"] = true })
        val body = TestAPI.getEGF("/api/tour/$tid/standings")
        val disposition = TestAPI.lastHeaders["Content-Disposition"] ?: fail("no Content-Disposition header")
        return disposition to body
    }

    @Test
    fun `EGF export of a swiss tournament is h9 with HA h0`() {
        val (disposition, body) = egfExport(aTournament)
        assertTrue(disposition.endsWith(".h9\""), "swiss games are even: $disposition")
        assertTrue(body.contains("; HA[h0]"), "HA header must say h0 for even games")
    }

    @Test
    fun `EGF export of a handicap McMahon uses the correction`() {
        val (disposition, body) = egfExport(aMMTournament) // handicap correction = 1
        assertTrue(disposition.endsWith(".h1\""), disposition)
        assertTrue(body.contains("; HA[h1]"))
    }

    @Test
    fun `EGF export of a McMahon at zero correction is h9`() {
        val evenMM = withPairing(aMMTournament, Json.Object("type" to "MAC_MAHON", "handicap" to Json.Object("correction" to 0)))
        val (disposition, body) = egfExport(evenMM)
        assertTrue(disposition.endsWith(".h9\""), disposition)
        assertTrue(body.contains("; HA[h0]"))
    }

    @Test
    fun `EGF export file name and header lines`() {
        val (disposition, body) = egfExport(aTournament)
        assertTrue(disposition.contains("filename=\"230510-marseille.h9\""), disposition)
        // 20 min + 10s Fischer, offline → adjusted 2400s → class C
        assertTrue(body.contains("; CL[C]"), body)
        assertTrue(body.contains("; TM[40]"), body)
        assertTrue(body.contains("; PC[FR,Marseille]"), body)
    }

    // --- manual game edit (0.26: white validated against the white id) ---

    private fun pairedGame(): Triple<Int, Json.Object, Int> {
        val tid = createTournament(aTournament)
        addPlayer(tid, aPlayer)
        addPlayer(tid, Json.MutableObject(anotherPlayer).also { it["final"] = true })
        val outsider = addPlayer(tid, Json.Object("name" to "Lupin", "firstname" to "Arsène", "rating" to 1500,
            "rank" to -6, "country" to "FR", "club" to "75Op", "final" to false))
        val games = TestAPI.post("/api/tour/$tid/pair/1", Json.Array("all")).asArray()
        val game = games.getJson(0)?.asObject() ?: fail("no game")
        return Triple(tid, game, outsider)
    }

    private fun game(tid: Int, id: Int?) = TestAPI.get("/api/tour/$tid/pair/1").asObject()
        .getArray("games")!!.map { it as Json.Object }.first { it.getInt("id") == id }

    @Test
    fun `manual game edit refuses an unknown white player`() {
        val (tid, game, _) = pairedGame()
        val resp = TestAPI.put("/api/tour/$tid/pair/1",
            Json.Object("id" to game.getInt("id"), "b" to game.getInt("b"), "w" to 9999, "h" to 0)).asObject()
        assertFalse(resp.getBoolean("success") ?: true)
        assertEquals("invalid white player id", resp.getString("error"))
    }

    @Test
    fun `manual game edit refuses a preliminary white player`() {
        val (tid, game, outsider) = pairedGame()
        val resp = TestAPI.put("/api/tour/$tid/pair/1",
            Json.Object("id" to game.getInt("id"), "b" to game.getInt("b"), "w" to outsider, "h" to 0)).asObject()
        assertEquals("white registration status is not final", resp.getString("error"))
    }

    @Test
    fun `an edit with an unknown player id leaves the game untouched`() {
        // used to store the unknown id in the game, then fail with a NullPointerException (HTTP 500)
        val (tid, game, _) = pairedGame()
        TestAPI.put("/api/tour/$tid/pair/1",
            Json.Object("id" to game.getInt("id"), "b" to game.getInt("b"), "w" to 9999, "h" to 0))
        val after = game(tid, game.getInt("id"))
        assertEquals(game.getInt("w"), after.getInt("w"), "a rejected edit must not change the white player")
        assertEquals(game.getInt("b"), after.getInt("b"))
    }

    @Test
    fun `manual game edit can swap colours`() {
        val (tid, game, _) = pairedGame()
        val resp = TestAPI.put("/api/tour/$tid/pair/1",
            Json.Object("id" to game.getInt("id"), "b" to game.getInt("w"), "w" to game.getInt("b"), "h" to 0)).asObject()
        assertTrue(resp.getBoolean("success") == true, resp.toString())
        val after = game(tid, game.getInt("id"))
        assertEquals(game.getInt("b"), after.getInt("w"))
        assertEquals(game.getInt("w"), after.getInt("b"))
    }

    @Test
    fun `pairing a round beyond the next one is refused`() {
        val tid = createTournament(aTournament)
        addPlayer(tid, aPlayer)
        val resp = TestAPI.post("/api/tour/$tid/pair/2", Json.Array("all")).asObject()
        // used to escape as a raw Error (HTTP 500): lastRound() is 1 even before any round is paired
        assertEquals("invalid round", resp.getString("error"))
    }

    // --- API bearer (0.26: an undecryptable bearer is a 401, not a 500) ---

    private fun requestWithBearer(bearer: String): HttpServletRequest = mock {
        on { getHeader(eq(TokenHandler.AUTH_HEADER)) } doReturn "${TokenHandler.AUTH_PREFIX} $bearer"
    }

    @Test
    fun `garbage or stale bearers mean not logged in`() {
        WebappManager.properties["auth"] = "none" // resolves the shared secret like the other tests
        assertNull(TokenHandler.getLoggedUser(requestWithBearer("garbage!")))
        val otherSecret = AESCryptograph().apply { init("another-secret!!") }
        assertNull(TokenHandler.getLoggedUser(requestWithBearer(otherSecret.webEncrypt("session:key"))))
    }

    @Test
    fun `a well-formed bearer for an unknown access key means not logged in`() {
        WebappManager.properties["auth"] = "none"
        val ours = AESCryptograph().apply { init(sharedSecret) }
        assertNull(TokenHandler.getLoggedUser(requestWithBearer(ours.webEncrypt("session:unknown-key"))))
        assertNull(TokenHandler.getLoggedUser(requestWithBearer(ours.webEncrypt("no-colon"))))
    }

    // --- TimeSystem ---

    @Test
    fun `adjusted time per time system`() {
        assertEquals(1200 + 120 * 10, FischerTime(1200, 10).adjustedTime())
        assertEquals(1200, FischerTime(1200, 0).adjustedTime())
        assertEquals(3600 + 60 * 300 / 15, CanadianByoyomi(3600, 300, 15).adjustedTime())
        assertEquals(2400 + 45 * 30, StandardByoyomi(2400, 30, 5).adjustedTime())
        assertEquals(1800, SuddenDeath(1800).adjustedTime())
    }

    @Test
    fun `time systems round-trip through JSON`() {
        listOf(FischerTime(1200, 10), FischerTime(1200, 10, 2400), CanadianByoyomi(3600, 300, 15),
            StandardByoyomi(2400, 30, 5), SuddenDeath(1800)).forEach {
            assertEquals(it, TimeSystem.fromJson(it.toJson()))
        }
    }
}
