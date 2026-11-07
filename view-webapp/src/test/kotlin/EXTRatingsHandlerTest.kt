package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.ratings.EXTRatingsHandler
import org.jeudego.pairgoth.web.BaseWebappManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EXTRatingsHandlerTest: TestBase() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            // keep RatingsManager's cache dir out of the source tree
            BaseWebappManager.properties.putIfAbsent("ratings.path", "target/test-ratings")
        }
    }

    private val roster = """
        {
          "date": "2026-07-24",
          "players": [
            {"name": "Yıldız", "firstname": "İbrahim", "country": "TR", "club": "Ank", "rank": "4k", "rating": 1700, "ext": "153"},
            {"name": "Şahin", "firstname": "Gökçe", "country": "TR", "club": "Ist", "rank": "1d", "rating": 2100, "ext": "154"}
          ]
        }
    """.trimIndent()

    @Test
    fun parseRoster() {
        val (date, players) = EXTRatingsHandler.parsePayload(roster)!!
        assertEquals(LocalDate.of(2026, 7, 24), date)
        assertEquals(2, players.size)
        val first = players[0] as com.republicate.kson.Json.Object
        assertEquals("EXT", first.getString("origin"))
        assertEquals("Yıldız", first.getString("name"))
        assertEquals("İbrahim", first.getString("firstname"))
        assertEquals("4k", first.getString("rank"))
        assertEquals(1700, first.getInt("rating"))
        assertEquals("153", first.getString("ext"))
    }

    @Test
    fun rejectMalformedPayloads() {
        assertNull(EXTRatingsHandler.parsePayload("not json"))
        assertNull(EXTRatingsHandler.parsePayload("""{"players": []}"""))
        assertNull(EXTRatingsHandler.parsePayload("""{"date": "yesterday", "players": []}"""))
        assertNull(EXTRatingsHandler.parsePayload("""{"date": "2026-07-24"}"""))
    }

    @Test
    fun fetchFromFileUrlIgnoresFreeze() {
        val fixture = java.io.File("target/test-roster.json").apply { writeText(roster) }
        BaseWebappManager.properties.setProperty("ratings.ext", fixture.toURI().toString())
        // a long-past global freeze must not stop the roster from refreshing
        BaseWebappManager.properties.setProperty("ratings.date", "2000-01-01")
        try {
            val players = EXTRatingsHandler.fetchPlayers()
            assertEquals(2, players.size)
            assertEquals("EXT", (players[1] as com.republicate.kson.Json.Object).getString("origin"))
            assertEquals(LocalDate.of(2026, 7, 24), LocalDate.from(EXTRatingsHandler.activeDate()))
        } finally {
            BaseWebappManager.properties.remove("ratings.ext")
            BaseWebappManager.properties.remove("ratings.date")
        }
    }

    @Test
    fun activeIffConfigured() {
        BaseWebappManager.properties.remove("ratings.ext")
        assertFalse(EXTRatingsHandler.active)
        BaseWebappManager.properties.setProperty("ratings.ext", "file:///tmp/roster.json")
        try {
            assertTrue(EXTRatingsHandler.active)
        } finally {
            BaseWebappManager.properties.remove("ratings.ext")
        }
    }
}
