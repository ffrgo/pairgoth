package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.ext.OpenGotha
import org.jeudego.pairgoth.util.XmlUtils
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ImportExportTests: TestBase() {

    @Test
    fun `001 test imports`() {
        getTestResources("opengotha/tournamentfiles/").forEach { file ->
            logger.info("reading resource ${file.canonicalPath}")
            val resource = file.readText(StandardCharsets.UTF_8)
            val resp = TestAPI.post("/api/tour", resource)
            val id = resp.asObject().getInt("id")
            val tournament = TestAPI.get("/api/tour/$id").asObject()
            logger.info(tournament.toString().slice(0..50) + "...")
            val players = TestAPI.get("/api/tour/$id/part").asArray()
            logger.info(players.toString().slice(0..50) + "...")
            for (round in 1..tournament.getInt("rounds")!!) {
                val games = TestAPI.get("/api/tour/$id/res/1").asArray()
                logger.info("games for round $round: {}", games.toString())
            }
            val xml = TestAPI.getXml("/api/tour/$id")
            logger.info(xml.slice(0..50)+"...")
        }
    }

    @Test
    fun `002 test opengotha import export`() {
        // We import a tournament
        // Check that after exporting and reimporting we get the same pairgoth tournament object
        getTestResources("opengotha").forEach { file ->
            val resource = file.readText(StandardCharsets.UTF_8)
            val root_xml = XmlUtils.parse(resource)
            val tournament = OpenGotha.import(root_xml)

            val exported = OpenGotha.export(tournament)
            val tournament2 = OpenGotha.import(XmlUtils.parse(exported))
            assert(tournament == tournament2)
        }
    }
}
