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
class BasicTests() {

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
        "pairing" to Json.Object(
            "type" to "ROUNDROBIN"
        )
    )

    @Test
    fun `001 create tournament`() {
        val resp = TestAPI.post("/api/tour", aTournament)
        assertTrue(resp.isObject, "Json object expected")
        assertTrue(resp.asObject().getBoolean("success") == true, "expecting success")
    }

    @Test
    fun `002 get tournament`() {
        val resp = TestAPI.get("/api/tour/1")
        assertTrue(resp.isObject, "Json object expected")
        assertEquals(1, resp.asObject().getInt("id"), "First tournament should have id #1")
        // filter out "id", and also "komi", "rules" and "gobanSize" which were provided by default
        val cmp = Json.Object(*resp.asObject().entries.filter { it.key !in listOf("id", "komi", "rules", "gobanSize") }.map { Pair(it.key, it.value) }.toTypedArray())
        assertEquals(aTournament.toString(), cmp.toString(), "tournament differs")
    }
}
