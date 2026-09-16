package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.ratings.FFGRatingsHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FFGRatingsHandlerTest: TestBase() {

    // V4 = V3 + the EGD PIN as last column; a V3 mirror (no column) must keep parsing
    private val echelle = """
        #Echelle au 16/09/2026 16:18:51
        AAIJ René                               268 e ------- xxxx NL --------
        ABAD Jahin                            -2900 - 2000205 38GJ FR 19574643
        ABELA Maxence                         -2400 L 2200232 92Co FR 20162219
        ABADIE Yves                           -1400 - 0452000 31To FR
    """.trimIndent()

    @Test
    fun parseV4() {
        val (date, players) = FFGRatingsHandler.parsePayload(echelle)!!
        assertEquals(LocalDate.of(2026, 9, 16), date)
        assertEquals(4, players.size)
        val byName = players.associate { (it as Json.Object).getString("name")!! to it }
        // unknown PIN: no key at all (the UI gates on field presence)
        assertNull(byName["AAIJ"]!!.getString("egf"))
        assertEquals("19574643", byName["ABAD"]!!.getString("egf"))
        assertEquals("2000205", byName["ABAD"]!!.getString("ffg"))
        assertEquals("L", byName["ABELA"]!!.getString("license"))
        assertEquals("20162219", byName["ABELA"]!!.getString("egf"))
        // V3 line (no PIN column) still parses
        assertNull(byName["ABADIE"]!!.getString("egf"))
        assertEquals("0452000", byName["ABADIE"]!!.getString("ffg"))
        assertEquals(-1400 + 2050, byName["ABADIE"]!!.getInt("rating"))
    }
}
