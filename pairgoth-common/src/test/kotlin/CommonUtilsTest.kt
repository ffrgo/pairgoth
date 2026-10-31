package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.Randomizer
import org.jeudego.pairgoth.util.displayRank
import org.jeudego.pairgoth.util.mutableBiMultiMapOf
import org.jeudego.pairgoth.util.mutableMultiMapOf
import org.jeudego.pairgoth.util.parse
import org.jeudego.pairgoth.util.toString
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommonUtilsTest {

    // --- AESCryptograph: the API bearer and session ids rely on it ---

    private fun cryptograph(key: String = "0123456789abcdef") = AESCryptograph().apply { init(key) }

    @Test
    fun `AES web round-trip`() {
        val c = cryptograph()
        val clear = "someone@example.com:1234567890"
        val token = c.webEncrypt(clear)
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]+")), "web token must be URL-safe base64 without padding: $token")
        assertEquals(clear, c.webDecrypt(token))
    }

    @Test
    fun `AES encryption is deterministic for a given key`() {
        // ECB without IV is intentional (see AESCryptograph doc): the same session id must give the same token
        assertEquals(cryptograph().webEncrypt("abc"), cryptograph().webEncrypt("abc"))
        assertNotEquals(cryptograph().webEncrypt("abc"), cryptograph("fedcba9876543210").webEncrypt("abc"))
    }

    @Test
    fun `AES only uses the first 16 key bytes`() {
        assertEquals(cryptograph("0123456789abcdef").webEncrypt("x"), cryptograph("0123456789abcdefIGNORED").webEncrypt("x"))
    }

    @Test
    fun `AES refuses a key shorter than 16 bytes`() {
        assertFailsWith<Error> { AESCryptograph().init("too short") }
    }

    @Test
    fun `AES decryption of garbage or of another key's token throws`() {
        // TokenHandler relies on this throwing to answer 401 instead of 500
        val c = cryptograph()
        assertFailsWith<RuntimeException> { c.webDecrypt("not-a-real-token") }
        val foreign = cryptograph("fedcba9876543210").webEncrypt("someone@example.com:1")
        val decoded = runCatching { c.webDecrypt(foreign) }
        assertTrue(decoded.isFailure || decoded.getOrNull() != "someone@example.com:1",
            "a token from another secret must never decrypt to the original")
    }

    // --- JsonIO: reader/writer adapters used by both servlets ---

    @Test
    fun `Json written to a Writer equals its toString`() {
        val json = Json.Object(
            "name" to "Hercule \"le belge\" Poirot",
            "path" to "a\\b/c",
            "multiline" to "one\ntwo\tthree",
            "accents" to "Élodie Çà ü",
            "list" to Json.Array(1, 2.5, true, null, "x\"y")
        )
        val writer = StringWriter()
        json.toString(writer)
        assertEquals(json.toString(), writer.toString())
    }

    @Test
    fun `Json parsed from a Reader round-trips`() {
        val text = """{"a":1,"b":[true,false,null],"c":"quote \" and \\ backslash","d":{"e":"Élodie"}}"""
        val parsed = Json.parse(StringReader(text)) ?: error("null parse")
        assertEquals(Json.parse(text), parsed)
    }

    // --- MultiMap / BiMultiMap: team tournaments index individual games with a BiMultiMap ---

    @Test
    fun `MultiMap groups values per key without duplicates`() {
        val map = mutableMultiMapOf(1 to "a", 1 to "b", 2 to "c")
        assertEquals<Set<String>?>(setOf("a", "b"), map[1])
        assertFalse(map.put(1, "a"), "adding an existing value reports no change")
        assertTrue(map.put(3, "d"))
        assertEquals(listOf(1, 2, 3), map.keys.toList(), "insertion order is kept")
    }

    // Known bug: LinkedHashBiMultiMap.remove(key): MutableSet<V>? compiles to `Set remove(Object)` with no
    // `Object remove(Object)` bridge, so calls through the Map interface reach HashMap.remove and skip the override.
    // Latent today: nothing reads `inverse` (TeamTournament.individualGames only uses the forward map).
    @Disabled("known bug: BiMultiMap.remove() bypassed through the Map interface, inverse not updated")
    @Test
    fun `BiMultiMap maintains its inverse`() {
        val map = mutableBiMultiMapOf(1 to "a", 1 to "b", 2 to "c")
        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 2), map.inverse)
        map.remove(1)
        assertEquals(mapOf("c" to 2), map.inverse, "removing a key drops its values from the inverse")
    }

    @Disabled("known bug: put() does not remove the value from its previous key")
    @Test
    fun `BiMultiMap moving a value to another key removes it from the old key`() {
        // "each value has only a distinct key" (MultiMap.kt doc)
        val map = mutableBiMultiMapOf(1 to "a", 1 to "b")
        map.put(2, "a")
        assertEquals(2, map.inverse["a"])
        assertEquals<Set<String>?>(setOf("b"), map[1], "the value must not remain under its previous key")
        assertEquals<Set<String>?>(setOf("a"), map[2])
    }

    // --- misc ---

    @Test
    fun `random strings are alphanumeric with the requested length`() {
        val s = Randomizer.randomString(64)
        assertEquals(64, s.length)
        assertTrue(s.all { it.isLetterOrDigit() && it.code < 128 })
        assertNotEquals(s, Randomizer.randomString(64))
    }

    @Test
    fun `displayRank around the kyu-dan boundary`() {
        assertEquals("30k", displayRank(-30))
        assertEquals("1k", displayRank(-1))
        assertEquals("1d", displayRank(0))
        assertEquals("9d", displayRank(8))
        assertEquals("3p", displayRank(5, 3))
        assertEquals("6d", displayRank(5, 0), "pro 0 means amateur")
    }
}
