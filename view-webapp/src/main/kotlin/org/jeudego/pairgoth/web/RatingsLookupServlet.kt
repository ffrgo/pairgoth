package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.jeudego.pairgoth.ratings.RatingsManager
import org.jeudego.pairgoth.util.parse
import org.jeudego.pairgoth.util.toString
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * POST /api/ratings-lookup
 * Body: { "egf": ["pin1", "pin2"], "ffg": ["lic1"], "aga": [...] }
 * Response: { "egf": {"pin1": {"rating": 2050, "pro": 0, "license": "L"}, ...}, "ffg": {...}, "aga": {...} }
 * `license` is present only for entries that carry it (FFG, and FR-propagated EGF).
 *
 * Used by the registration-tab "Refresh ratings" feature to look up current rating + pro
 * for already-registered players in a single round-trip. Only IDs found in the active
 * RatingsManager state are returned; missing entries are simply absent from the response.
 */
class RatingsLookupServlet : HttpServlet() {

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "application/json; charset=UTF-8"
        try {
            val body = Json.parse(request.reader)?.asObject()
                ?: return error(response, HttpServletResponse.SC_BAD_REQUEST, "expected JSON object body")
            val want = SOURCES.associateWith { src ->
                body.getArray(src)?.map { it.toString() }?.toSet().orEmpty()
            }.filterValues { it.isNotEmpty() }
            if (want.isEmpty()) {
                response.writer.write(Json.MutableObject().toString())
                return
            }

            val out = Json.MutableObject()
            val players = RatingsManager.players  // read-only snapshot reference
            for ((src, ids) in want) {
                val bucket = Json.MutableObject()
                val originUpper = src.uppercase()
                for (any in players) {
                    val player = any as? Json.Object ?: continue
                    if (player.getString("origin") != originUpper) continue
                    val id = player.getString(src) ?: continue
                    if (id !in ids) continue
                    bucket[id] = Json.MutableObject().also {
                        player.getInt("rating")?.let { r -> it["rating"] = r }
                        (player["pro"] as? Number)?.toInt()?.takeIf { p -> p > 0 }?.let { p -> it["pro"] = p }
                        // FFG echelle licence status for FR tournaments (also propagated onto FR EGF entries)
                        player.getString("license")?.let { lic -> it["license"] = lic }
                    }
                }
                if (!bucket.isEmpty()) out[src] = bucket
            }
            out.toString(response.writer)
        } catch (e: Exception) {
            logger.error("ratings-lookup failed", e)
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message ?: "lookup failed")
        }
    }

    private fun error(response: HttpServletResponse, code: Int, message: String) {
        response.status = code
        val safe = message.replace("\"", "\\\"")
        response.writer.write("""{"success":false,"error":"$safe"}""")
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ratings-lookup")
        private val SOURCES = listOf("egf", "ffg", "aga")
    }
}
