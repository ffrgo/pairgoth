package org.jeudego.pairgoth.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.Randomizer
import java.util.concurrent.TimeUnit

/**
 * External-auth SSO ticket. The fronting site (EGC) authenticates the operator, then hands them over
 * to pairgoth on `/sso?ticket=...&goto=...`. The ticket is AES-encrypted with `auth.shared_secret`
 * (URL-safe base64, same cryptograph as the API bearers), payload `email:expiryMillis:nonce`,
 * valid once and shortly.
 */
object SsoTicket {
    // longest accepted remaining lifetime; also bounds the consumed-nonces retention, so a nonce
    // can never be replayed after its cache eviction
    private const val MAX_TTL_MILLIS = 2 * 60_000L

    private val cryptograph = AESCryptograph().apply { init(sharedSecret) }
    private val consumed: Cache<String, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(2 * MAX_TTL_MILLIS, TimeUnit.MILLISECONDS)
        .maximumSize(10_000)
        .build()

    /** The authenticated email, or null when the ticket is malformed, expired or replayed. */
    fun validate(ticket: String): String? {
        val clear = try { cryptograph.webDecrypt(ticket) } catch (t: Throwable) { return null }
        // parse from the right: emails may legally contain ':'
        val lastColon = clear.lastIndexOf(':')
        val midColon = if (lastColon > 0) clear.lastIndexOf(':', lastColon - 1) else -1
        if (midColon <= 0) return null
        val email = clear.substring(0, midColon)
        val expiry = clear.substring(midColon + 1, lastColon).toLongOrNull() ?: return null
        val nonce = clear.substring(lastColon + 1)
        val now = System.currentTimeMillis()
        if (email.isBlank() || nonce.isEmpty()) return null
        if (now > expiry || expiry > now + MAX_TTL_MILLIS) return null
        if (consumed.asMap().putIfAbsent(nonce, true) != null) return null // one-time
        return email
    }

    /** Mints a ticket — the fronting site's half of the contract, here for tests and the curl helper. */
    fun mint(email: String, ttlMillis: Long = 60_000L, nonce: String = Randomizer.randomString(16)): String =
        cryptograph.webEncrypt("$email:${System.currentTimeMillis() + ttlMillis}:$nonce")

    /** Open-redirect guard: only a single-leading-slash relative path passes; anything else → default. */
    fun safeGoto(goto: String?): String =
        if (goto != null && goto.startsWith("/") && !goto.startsWith("//") && !goto.contains('\\')) goto
        else "/index"
}
