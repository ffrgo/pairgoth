package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Criterion
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * "Different tiebreakers might be used for different purposes... Pairing programs should allow
 * such." (EGF tournament system rules). The criteria ordering the players when pairing a round
 * can now differ from the ones ordering the final results; left unset, both are the same list.
 */
class PairingCriteriaTest : TestBase() {

    @Test
    fun `unset, the pairing order follows the standings criteria`() {
        val pairing = MacMahon()
        assertNull(pairing.pairingPlacementParams)
        assertEquals(pairing.placementParams.criteria, pairing.pairingPlacement.criteria)
    }

    @Test
    fun `a dedicated pairing order is kept, serialized and cleared`() {
        val standings = Json.Array("MMS", "EGFDC", "SOSM")
        val json = Json.Object("type" to "MAC_MAHON", "placement" to standings,
            "pairingPlacement" to Json.Array("MMS", "SOSM", "SOSOSM", "NONE"))
        val pairing = Pairing.fromJson(json, null)
        assertEquals(listOf(Criterion.MMS, Criterion.EGFDC, Criterion.SOSM), pairing.placementParams.criteria)
        assertEquals(listOf(Criterion.MMS, Criterion.SOSM, Criterion.SOSOSM, Criterion.NONE),
            pairing.pairingPlacement.criteria)

        // it survives serialization
        val reloaded = Pairing.fromJson(pairing.toJson(), null)
        assertEquals(pairing.pairingPlacement.criteria, reloaded.pairingPlacement.criteria)

        // an untouched update keeps it...
        val untouched = Pairing.fromJson(Json.Object("type" to "MAC_MAHON"), pairing)
        assertEquals(pairing.pairingPlacement.criteria, untouched.pairingPlacement.criteria)
        // ...and an all-NONE list clears it back to the standings criteria
        val cleared = Pairing.fromJson(
            Json.Object("type" to "MAC_MAHON", "pairingPlacement" to Json.Array("NONE", "NONE", "NONE", "NONE")),
            pairing)
        assertNull(cleared.pairingPlacementParams)
        assertEquals(cleared.placementParams.criteria, cleared.pairingPlacement.criteria)
    }

    // "Only one of SOS-2, SOS-1, or SOS may be used" (EGF), and one direct comparison is enough
    @Test
    fun `incoherent criteria are refused, both lists`() {
        MemoryStore.reset()
        fun create(placement: Json.Array, pairingPlacement: Json.Array? = null): Json.Object {
            val pairing = Json.MutableObject("type" to "MAC_MAHON", "placement" to placement)
            pairingPlacement?.let { pairing["pairingPlacement"] = it }
            return TestAPI.post("/api/tour", Json.Object(
                "type" to "INDIVIDUAL", "name" to "Bad", "shortName" to "bad",
                "startDate" to "2026-08-12", "endDate" to "2026-08-12",
                "country" to "FR", "location" to "Paris", "online" to false,
                "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
                "rounds" to 2, "pairing" to pairing)).asObject()
        }
        create(Json.Array("MMS", "SOSM", "SOSMM1")).also {
            assertEquals(false, it.getBoolean("success"), "two SOS flavours must be refused")
            assertTrue(it.getString("error")!!.contains("only one of SOS"), "expecting the EGF rule's message")
        }
        create(Json.Array("MMS", "EGFDC", "DC")).also {
            assertEquals(false, it.getBoolean("success"), "two direct confrontations must be refused")
        }
        create(Json.Array("MMS", "SOSM", "SOSM")).also {
            assertEquals(false, it.getBoolean("success"), "a repeated criterion must be refused")
        }
        // the pairing list is checked too
        create(Json.Array("MMS", "SOSM"), Json.Array("MMS", "SOSM", "SOSMM2")).also {
            assertEquals(false, it.getBoolean("success"))
            assertTrue(it.getString("error")!!.contains("pairing criteria"))
        }
        // and a sound list still goes through
        assertEquals(true, create(Json.Array("MMS", "EGFDC", "SOSM", "NONE")).getBoolean("success"))
    }

    @Test
    fun `the dedicated order drives the draw, the standings keep their own`() {
        MemoryStore.reset()
        val tour = Json.Object(
            "type" to "INDIVIDUAL", "name" to "Split", "shortName" to "split",
            "startDate" to "2026-08-12", "endDate" to "2026-08-12",
            "country" to "FR", "location" to "Paris", "online" to false,
            "timeSystem" to Json.Object("type" to "FISCHER", "mainTime" to 600, "increment" to 10),
            "rounds" to 2,
            "pairing" to Json.Object("type" to "SWISS",
                // the standings order players by rating, the draw by lottery
                "placement" to Json.Array("NBW", "RATING"),
                "pairingPlacement" to Json.Array("NBW", "LOTTERY"))
        )
        val tourId = TestAPI.post("/api/tour", tour).asObject().getInt("id") ?: fail("no tournament id")
        val get = TestAPI.get("/api/tour/$tourId").asObject().getObject("pairing")!!
        assertEquals("[\"NBW\",\"RATING\"]", get.getArray("placement").toString())
        assertEquals("[\"NBW\",\"LOTTERY\"]", get.getArray("pairingPlacement").toString())
        // pairing still works with criteria the standings never see
        for (name in listOf("Aaa", "Bbb", "Ccc", "Ddd")) {
            TestAPI.post("/api/tour/$tourId/part", Json.Object("name" to name, "firstname" to "X",
                "rating" to 1800, "rank" to -2, "country" to "FR", "club" to "13Ma", "final" to true))
        }
        assertEquals(2, TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().size)
        assertTrue(TestAPI.get("/api/tour/$tourId/standings/1").asArray().isNotEmpty())
    }
}
