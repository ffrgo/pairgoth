package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.ratings.PlayerIndex
import org.jeudego.pairgoth.ratings.RatingsManager.Ratings
import org.jeudego.pairgoth.web.BaseWebappManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PlayerIndexTest: TestBase() {
    companion object {
        val ALL = Ratings.values().fold(0) { a, r -> a or r.flag }

        fun player(origin: String, name: String, firstname: String, country: String) = Json.MutableObject().also {
            it["origin"] = origin
            it["name"] = name
            it["firstname"] = firstname
            it["country"] = country
        }

        val players = Json.MutableArray().also {
            it.add(player("AGA", "Smith", "John", "US"))
            it.add(player("EGF", "Smith", "Anna", "DE"))
            it.add(player("FFG", "Smith", "Jean", "FR"))
            it.add(player("EXT", "Smith", "Ali", "TR"))
            it.add(player("EXT", "Yıldız", "İbrahim", "TR"))
        }

        val index = PlayerIndex()

        @BeforeAll
        @JvmStatic
        fun setup() {
            // keep RatingsManager's cache dir out of the source tree
            BaseWebappManager.properties.putIfAbsent("ratings.path", "target/test-ratings")
            index.build(players)
        }
    }

    private fun origins(hits: List<Int>) = hits.map { (players[it] as Json.Object).getString("origin") }.toSet()

    @Test
    fun fullMaskMatchesEverySource() {
        assertEquals(setOf("AGA", "EGF", "FFG", "EXT"), origins(index.match("smith", ALL, ALL, null)))
    }

    @Test
    fun singleSourceFilter() {
        assertEquals(setOf("EXT"), origins(index.match("smith", Ratings.EXT.flag, ALL, null)))
        assertEquals(setOf("EGF"), origins(index.match("smith", Ratings.EGF.flag, ALL, null)))
    }

    @Test
    fun multiSourceFilter() {
        assertEquals(
            setOf("EGF", "FFG"),
            origins(index.match("smith", Ratings.EGF.flag or Ratings.FFG.flag, ALL, null))
        )
        assertEquals(
            setOf("AGA", "EGF", "EXT"),
            origins(index.match("smith", Ratings.AGA.flag or Ratings.EGF.flag or Ratings.EXT.flag, ALL, null))
        )
    }

    @Test
    fun maskCoveringActiveSourcesSkipsFilter() {
        // active sources = EGF|FFG only: requesting exactly those must behave like no filter
        val active = Ratings.EGF.flag or Ratings.FFG.flag
        assertEquals(setOf("AGA", "EGF", "FFG", "EXT"), origins(index.match("smith", active, active, null)))
    }

    @Test
    fun emptyMaskMatchesNothing() {
        assertTrue(index.match("smith", 0, ALL, null).isEmpty())
    }

    @Test
    fun countryFilter() {
        assertEquals(setOf("EXT"), origins(index.match("smith", ALL, ALL, "tr")))
    }

    @Test
    fun turkishCharacters() {
        val hits = index.match("yıldız", Ratings.EXT.flag, ALL, null)
        assertEquals(1, hits.size)
        assertEquals("İbrahim", (players[hits[0]] as Json.Object).getString("firstname"))
    }
}
