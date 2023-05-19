package org.jeudego.pairgoth.test

import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class ImportTests: TestBase() {

    @Test
    fun `001 test imports`() {
        getTestResources("opengotha").forEach { file ->
            logger.info("reading resource ${file.canonicalPath}")
            val resource = file.readText(StandardCharsets.UTF_8)
            val resp = TestAPI.post("/api/tour", resource)
            val id = resp.asObject().getInt("id")
            val tournament = TestAPI.get("/api/tour/$id")
            logger.info(tournament.toString())
        }
    }
}