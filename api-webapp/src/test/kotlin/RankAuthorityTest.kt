package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.server.WebappManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `ratings.rank_authoritative`: on bulk imports, an out-of-band rating is snapped to the
 * rank's nominal value so imported players stay chained; in-band ratings are kept as-is.
 * Off by default (rating stays authoritative, discrepancies show as unlinked).
 */
class RankAuthorityTest : TestBase() {

    companion object {
        val tournament = Json.Object(
            "type" to "INDIVIDUAL",
            "name" to "Rank Authority Test",
            "shortName" to "rank-authority-test",
            "startDate" to "2026-07-25",
            "endDate" to "2026-07-25",
            "country" to "FR",
            "location" to "Test Location",
            "online" to false,
            "timeSystem" to Json.Object("type" to "SUDDEN_DEATH", "mainTime" to 3600),
            "rounds" to 1,
            "pairing" to Json.Object("type" to "MAC_MAHON")
        )
    }

    @AfterEach
    fun cleanup() {
        WebappManager.properties.remove("ratings.rank_authoritative")
    }

    private fun createTournament(): Int =
        TestAPI.post("/api/tour", tournament).asObject().getInt("id") ?: throw Error("tournament creation failed")

    private fun player(tourId: Int, ext: String): Json.Object {
        val all = TestAPI.get("/api/tour/$tourId/part").asArray()
        return all.map { it as Json.Object }.first { it.getString("ext") == ext }
    }

    @Test
    fun `default off - discrepant rating imported as is`() {
        val tourId = createTournament()
        TestAPI.post("/api/tour/$tourId/part", Json.Array(Json.Object(
            "name" to "Unlinked", "firstname" to "Uma", "country" to "FR", "club" to "13Ma",
            "rating" to 1850, "rank" to 0, "ext" to "u1" // 1d rank, 3k rating
        )))
        val p = player(tourId, "u1")
        assertEquals(1850, p.getInt("rating"))
        assertEquals(0, p.getInt("rank"))
    }

    @Test
    fun `on - out-of-band rating snapped, in-band kept, pro symmetric`() {
        WebappManager.properties["ratings.rank_authoritative"] = "true"
        val tourId = createTournament()
        TestAPI.post("/api/tour/$tourId/part", Json.Array(
            Json.Object("name" to "Snapped", "firstname" to "Sam", "country" to "FR", "club" to "13Ma",
                "rating" to 1850, "rank" to 0, "ext" to "s1"),               // 1d rank, 3k rating → 2100
            Json.Object("name" to "Kept", "firstname" to "Kim", "country" to "FR", "club" to "13Ma",
                "rating" to 2130, "rank" to 0, "ext" to "k1"),               // in 1d band → kept
            Json.Object("name" to "Pro", "firstname" to "Pat", "country" to "FR", "club" to "13Ma",
                "rating" to 2500, "rank" to 6, "pro" to 3, "ext" to "p1")    // 3p → 2760
        ))
        assertEquals(2100, player(tourId, "s1").getInt("rating"))
        assertEquals(2130, player(tourId, "k1").getInt("rating"))
        assertEquals(2760, player(tourId, "p1").getInt("rating"))
    }

    @Test
    fun `on - resync re-snaps a manual unchain, lock stays immune`() {
        WebappManager.properties["ratings.rank_authoritative"] = "true"
        val tourId = createTournament()
        TestAPI.post("/api/tour/$tourId/part", Json.Array(
            Json.Object("name" to "Manual", "firstname" to "Mel", "country" to "FR", "club" to "13Ma",
                "rating" to 2130, "rank" to 0, "ext" to "m1"),
            Json.Object("name" to "Pinned", "firstname" to "Pia", "country" to "FR", "club" to "13Ma",
                "rating" to 2350, "rank" to 3, "ext" to "l1", "locked" to true)
        ))
        // referee unchains m1 by hand (single PUT is never restricted)
        val m1 = player(tourId, "m1")
        TestAPI.put("/api/tour/$tourId/part/${m1.getInt("id")}", Json.Object("id" to m1.getInt("id"), "rating" to 1900))
        assertEquals(1900, player(tourId, "m1").getInt("rating"))
        // next sync: m1 re-snapped to its rank, locked l1 untouched despite discrepant wire values
        TestAPI.post("/api/tour/$tourId/part", Json.Array(
            Json.Object("ext" to "m1", "rating" to 1900, "rank" to 0),
            Json.Object("ext" to "l1", "rating" to 2000, "rank" to 1, "locked" to true)
        ))
        assertEquals(2100, player(tourId, "m1").getInt("rating"))
        assertEquals(2350, player(tourId, "l1").getInt("rating"))
        assertEquals(3, player(tourId, "l1").getInt("rank"))
    }
}
