package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.view.PairgothTool
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MmsMapTest: TestBase() {

    private fun player(name: String, mms: Double, rating: Int) =
        Json.Object("name" to name, "MMS" to mms, "rating" to rating)

    // super group (bar+1) is a catch-all: an OpenGotha-imported correction beyond +1
    // must land there instead of vanishing from the dialog
    @Test
    fun `mms map clamps keys above the super group`() {
        val tool = PairgothTool()
        val map = tool.getMmsMap(listOf(
            player("under", 33.0, 2350),
            player("top", 34.0, 2450),
            player("super", 35.0, 2550),
            player("over-corrected", 36.0, 2650)
        ), 35)
        assertEquals(listOf("under"), map[33L]?.map { it.getString("name") })
        assertEquals(listOf("top"), map[34L]?.map { it.getString("name") })
        assertEquals(listOf("over-corrected", "super"), map[35L]?.map { it.getString("name") },
            "keys above bar+1 collapse into the super group, sorted by rating")
        assertNull(map[36L])
    }
}
