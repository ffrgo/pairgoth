package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.store.historySnapshotName
import org.jeudego.pairgoth.store.parseHistorySnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryNamingTest {

    @Test
    fun `builds and parses a snapshot name round-trip`() {
        val name = historySnapshotName("000017-myshort.tour", "20260602153000", 0, "add-player")
        assertEquals("000017-myshort.tour-20260602153000-00-add-player", name)
        val parsed = parseHistorySnapshot(name)!!
        assertEquals("20260602153000", parsed.timestamp)
        assertEquals(0, parsed.seq)
        assertEquals("add-player", parsed.slug)
    }

    @Test
    fun `2-digit sequence is zero-padded and parsed back`() {
        val parsed = parseHistorySnapshot(historySnapshotName("7-t.tour", "20260602153000", 7, "pair"))!!
        assertEquals(7, parsed.seq)
        assertEquals("pair", parsed.slug)
    }

    @Test
    fun `tolerates legacy name without seq nor slug`() {
        val parsed = parseHistorySnapshot("000017-myshort.tour-20260602153000")!!
        assertEquals("20260602153000", parsed.timestamp)
        assertEquals(0, parsed.seq)
        assertNull(parsed.slug)
    }

    @Test
    fun `order key is chronological across the same second`() {
        val a = parseHistorySnapshot("3-t.tour-20260602153000-00-pair")!!
        val b = parseHistorySnapshot("3-t.tour-20260602153000-01-enter-result")!!
        val c = parseHistorySnapshot("3-t.tour-20260602153001-00-unpair")!!
        assertEquals(listOf(a, b, c), listOf(c, b, a).sortedBy { it.order })
    }

    @Test
    fun `non-snapshot names parse to null`() {
        assertNull(parseHistorySnapshot("000017-myshort.tour"))         // the live file, no suffix
        assertNull(parseHistorySnapshot("not-a-snapshot.txt"))
    }
}
