package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.web.BaseWebappManager
import org.jeudego.pairgoth.web.SsoTicket
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SsoTicketTest: TestBase() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun secret() {
            // honour an externally-provided secret (the mint helper must match the running server)
            BaseWebappManager.properties.setProperty("auth.external.secret",
                System.getProperty("pairgoth.auth.external.secret") ?: "0123456789ABCDEF")
        }
    }

    @Test
    fun roundtrip() {
        assertEquals("alice@egc.com", SsoTicket.validate(SsoTicket.mint("alice@egc.com")))
    }

    @Test
    fun colonInEmail() {
        // payload is parsed from the right, so a legal-but-exotic ':' in the email survives
        assertEquals("weird:user@egc.com", SsoTicket.validate(SsoTicket.mint("weird:user@egc.com")))
    }

    @Test
    fun expired() {
        assertNull(SsoTicket.validate(SsoTicket.mint("alice@egc.com", ttlMillis = -1000)))
    }

    @Test
    fun tooFarInTheFuture() {
        assertNull(SsoTicket.validate(SsoTicket.mint("alice@egc.com", ttlMillis = 10 * 60_000)))
    }

    @Test
    fun replayed() {
        val ticket = SsoTicket.mint("alice@egc.com")
        assertNotNull(SsoTicket.validate(ticket))
        assertNull(SsoTicket.validate(ticket))
    }

    @Test
    fun garbage() {
        assertNull(SsoTicket.validate("not-a-ticket"))
        assertNull(SsoTicket.validate(""))
    }

    @Test
    fun gotoGuard() {
        assertEquals("/tour?id=5", SsoTicket.safeGoto("/tour?id=5"))
        assertEquals("/index", SsoTicket.safeGoto(null))
        assertEquals("/index", SsoTicket.safeGoto("https://evil.example"))
        assertEquals("/index", SsoTicket.safeGoto("//evil.example"))
        assertEquals("/index", SsoTicket.safeGoto("/\\evil.example"))
        assertEquals("/index", SsoTicket.safeGoto("relative"))
    }

    // Mint helper, to curl a running instance before the EGC client exists:
    //   mvn -pl view-webapp test -Dtest='SsoTicketTest#mintHelper' \
    //       -Dpairgoth.auth.external.secret=<the server's secret> -Dticket.email=alice@egc.com
    // then browse: /sso?ticket=<printed>&goto=/index
    @Test
    fun mintHelper() {
        val email = System.getProperty("ticket.email") ?: return
        val ttl = (System.getProperty("ticket.ttl") ?: "60").toLong() * 1000
        println("/sso?ticket=${SsoTicket.mint(email, ttl)}")
    }
}
