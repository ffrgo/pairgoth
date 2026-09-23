package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.ApiClient.JsonApiClient
import org.jeudego.pairgoth.util.ApiClient.header
import org.slf4j.LoggerFactory

/**
 * The consumer's `/health` answer, and with it which webhook endpoints it implements
 * (`capabilities`; absent = all of them, consumers predating the field). Probed at startup;
 * a consumer unreachable then (a docker peer not up yet) is probed again from the next
 * tournament page, at most once a minute, and treated as complete meanwhile.
 */
object Webhook {
    val ALL = listOf("players", "presences", "pairings", "standings")

    val url: String? by lazy {
        WebappManager.properties.getProperty("webhook.url")?.trim()?.takeIf { it.isNotBlank() }?.removeSuffix("/")
    }
    val configured get() = url != null

    @Volatile private var known: Set<String>? = null
    @Volatile private var nextProbe = 0L

    /** capability → implemented, for the templates (all false when no webhook is configured) */
    fun capabilities(): Map<String, Boolean> {
        if (!configured) return ALL.associateWith { false }
        val set = known ?: (if (System.currentTimeMillis() >= nextProbe) probe() else null) ?: ALL.toSet()
        return ALL.associateWith { it in set }
    }

    /** one `/health` round trip, logged; the capabilities when healthy, else null */
    fun probe(): Set<String>? {
        val base = url ?: return null
        val secret = WebappManager.properties.getProperty("webhook.secret").orEmpty()
        return try {
            val resp = JsonApiClient.get("$base/health", header("X-Pairgoth-Secret", secret)) as Json.Object
            if (resp.getBoolean("status") == true) {
                val caps = resp.getArray("capabilities")?.map { it.toString() }?.toSet() ?: ALL.toSet()
                logger.info("webhook at $base healthy: ${resp.getString("name") ?: "(unnamed)"} — capabilities: ${caps.joinToString(", ")}")
                caps.also { known = it }
            } else {
                logger.warn("webhook at $base reachable but unhealthy: ${resp.getString("message") ?: "(no message)"}")
                null
            }
        } catch (e: Exception) {
            logger.warn("webhook at $base not reachable (${e.message}); assuming every capability until it answers")
            null
        }.also { if (it == null) nextProbe = System.currentTimeMillis() + RETRY_MS }
    }

    private const val RETRY_MS = 60_000L
    private val logger = LoggerFactory.getLogger("webhook")
}
