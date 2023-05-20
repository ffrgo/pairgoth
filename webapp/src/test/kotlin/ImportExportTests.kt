package org.jeudego.pairgoth.test

import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ImportExportTests: TestBase() {

    @Test
    fun `001 test imports`() {
        getTestResources("opengotha").forEach { file ->
            logger.info("reading resource ${file.canonicalPath}")
            val resource = file.readText(StandardCharsets.UTF_8)
            val resp = TestAPI.post("/api/tour", resource)
            val id = resp.asObject().getInt("id")
            val tournament = TestAPI.get("/api/tour/$id").asObject()
            logger.info(tournament.toString())
            val players = TestAPI.get("/api/tour/$id/part").asArray()
            logger.info(players.toString())
            for (round in 1..tournament.getInt("rounds")!!) {
                val games = TestAPI.get("/api/tour/$id/res/1").asArray()
                logger.info("games for round $round: {}", games.toString())
            }
            val xml = TestAPI.getXml("/api/tour/$id")
            logger.info(xml)
        }
    }
}
