package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@TestMethodOrder(Alphanumeric::class)
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

    val aPlayer = Json.Object(
        "name" to "Burma",
        "firstname" to "Nestor",
        "rating" to 1600,
        "rank" to -2,
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

    @Test
    fun `001 create tournament`() {
        val resp = TestAPI.post("/api/tour", aTournament) as Json.Object
        assertTrue(resp.getBoolean("success") == true, "expecting success")
    }

    @Test
    fun `002 get tournament`() {
        val resp = TestAPI.get("/api/tour/1") as Json.Object
        assertEquals(1, resp.getInt("id"), "First tournament should have id #1")
        // filter out "id", and also "komi", "rules" and "gobanSize" which were provided by default
        val cmp = Json.Object(*resp.entries.filter { it.key !in listOf("id", "komi", "rules", "gobanSize") }.map { Pair(it.key, it.value) }.toTypedArray())
        assertEquals(aTournament.toString(), cmp.toString(), "tournament differs")
    }

    @Test
    fun `003 register user`() {
        val resp = TestAPI.post("/api/tour/1/part", aPlayer) as Json.Object
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val players = TestAPI.get("/api/tour/1/part") as Json.Array
        val player = players[0] as Json.Object
        assertEquals(1, player.getInt("id"), "First player should have id #1")
        // filter out "id"
        val cmp = Json.Object(*player.entries.filter { it.key != "id" }.map { Pair(it.key, it.value) }.toTypedArray())
        assertEquals(aPlayer.toString(), cmp.toString(), "player differs")
    }

    @Test
    fun `004 modify user`() {
        // remove player #1 from round #2
        val resp = TestAPI.put("/api/tour/1/part/1", Json.Object("skip" to Json.Array(2))) as Json.Object
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val player = TestAPI.get("/api/tour/1/part/1") as Json.Object
        assertEquals("[2]", player.getArray("skip").toString(), "First player should have id #1")
    }

    @Test
    fun `005 pair`() {
        val resp = TestAPI.post("/api/tour/1/part", anotherPlayer) as Json.Object
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val games = TestAPI.post("/api/tour/1/pair/1", Json.Array("all"))
        val possibleResults = setOf(
            "[{\"id\":1,\"w\":1,\"b\":2,\"r\":\"?\"}]",
            "[{\"id\":1,\"w\":2,\"b\":1,\"r\":\"?\"}]")
        assertTrue(possibleResults.contains(games.toString()), "pairing differs")
    }
}
