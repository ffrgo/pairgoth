package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacMahonTest {
    @Test
    fun `Mac Mahon with handicap`() {
        var resp = TestAPI.post("/api/tour", BasicTests.aMMTournament).asObject()
        val tourId = resp.getInt("id") ?: throw Error("tournament creation failed")
        resp = TestAPI.post("/api/tour/$tourId/part", BasicTests.aPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val p1 = resp.getInt("id")!!
        resp = TestAPI.post("/api/tour/$tourId/part", BasicTests.anotherPlayer).asObject().also { assertTrue(it.getBoolean("success")!!) }
        val p2 = resp.getInt("id")!!
        val game = TestAPI.post("/api/tour/$tourId/pair/1", Json.Array("all")).asArray().getObject(0) ?: throw Error("pairing failed")
        assertEquals(p2, game.getInt("w"))
        assertEquals(p1, game.getInt("b"))
        assertEquals(3, game.getInt("h"))
    }

}