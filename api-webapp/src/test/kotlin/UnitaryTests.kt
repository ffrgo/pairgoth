package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.displayRank
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.parseRank
import org.jeudego.pairgoth.model.parseRankAndPro
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class UnitaryTests: TestBase() {

    @Test
    fun `010 ratingToRank EGD canonical buckets`() {
        // 1d/1k boundary at 2050 (EGD GoR2Rank: round($gor/100)-1)
        assertEquals(0, Pairable.ratingToRank(2050))   // 1d
        assertEquals(0, Pairable.ratingToRank(2149))   // 1d
        assertEquals(1, Pairable.ratingToRank(2150))   // 2d
        assertEquals(-1, Pairable.ratingToRank(2049))  // 1k
        assertEquals(-1, Pairable.ratingToRank(2000))  // 1k centre
        assertEquals(-1, Pairable.ratingToRank(1950))  // still 1k (matches EGD)
        assertEquals(-2, Pairable.ratingToRank(1949))  // 2k
        assertEquals(-2, Pairable.ratingToRank(1850))  // still 2k
        assertEquals(-3, Pairable.ratingToRank(1849))  // 3k
    }

    @Test
    fun `011 ratingToRank clamps to bounds`() {
        assertEquals(Pairable.MIN_RANK, Pairable.ratingToRank(-9999))
        assertEquals(Pairable.MIN_RANK, Pairable.ratingToRank(-951))   // 30k floor
        assertEquals(Pairable.MAX_RANK, Pairable.ratingToRank(2950))   // 9d ceiling
        assertEquals(Pairable.MAX_RANK, Pairable.ratingToRank(9999))
    }

    @Test
    fun `012 rankToRating round-trip`() {
        for (r in Pairable.MIN_RANK..Pairable.MAX_RANK) {
            assertEquals(r, Pairable.ratingToRank(Pairable.rankToRating(r)))
        }
    }

    @Test
    fun `013 FFG raw rating shifted matches EGD bucketing`() {
        // FFG raw = EGD GoR - 2050. The previous FFGRatingsHandler had off-by-ones at every -100 step.
        assertEquals("1k", displayRank(Pairable.ratingToRank(-50 + 2050)))   // FFG 1k centre
        assertEquals("1k", displayRank(Pairable.ratingToRank(-100 + 2050)))  // boundary, was 2k
        assertEquals("2k", displayRank(Pairable.ratingToRank(-101 + 2050)))
        assertEquals("2k", displayRank(Pairable.ratingToRank(-150 + 2050)))  // FFG 2k centre
        assertEquals("2k", displayRank(Pairable.ratingToRank(-200 + 2050)))  // boundary, was 3k
        assertEquals("3k", displayRank(Pairable.ratingToRank(-201 + 2050)))
        assertEquals("1d", displayRank(Pairable.ratingToRank(50 + 2050)))    // FFG 1d centre
    }

    @Test
    fun `014 parseRank accepts valid k and d, returns null on garbage and on p`() {
        assertEquals(0, Pairable.parseRank("1d"))
        assertEquals(8, Pairable.parseRank("9d"))
        assertEquals(-1, Pairable.parseRank("1k"))
        assertEquals(-30, Pairable.parseRank("30k"))
        assertEquals(-1, Pairable.parseRank("1K"))      // case-insensitive
        assertEquals(0, Pairable.parseRank(" 1d "))     // trim
        assertNull(Pairable.parseRank(""))
        assertNull(Pairable.parseRank("foo"))
        assertNull(Pairable.parseRank("0d"))            // was silently mapped to 1k
        assertNull(Pairable.parseRank("0k"))            // was silently mapped to 1d
        assertNull(Pairable.parseRank("10d"))           // beyond MAX_RANK
        assertNull(Pairable.parseRank("31k"))           // beyond MIN_RANK
        assertNull(Pairable.parseRank("1p"))            // pro: not yet supported
        assertNull(Pairable.parseRank("9p"))
    }

    @Test
    fun `015 ratingToPro EGD canonical pro buckets`() {
        // EGD: 1p = 2700, 2p = 2730, ..., 9p = 2940. round((gor-2700)/30)+1.
        assertEquals(1, Pairable.ratingToPro(2700))
        assertEquals(1, Pairable.ratingToPro(2714))   // round to 1p
        assertEquals(2, Pairable.ratingToPro(2715))   // boundary 1p/2p
        assertEquals(2, Pairable.ratingToPro(2730))   // 2p centre
        assertEquals(9, Pairable.ratingToPro(2940))   // 9p
        assertEquals(9, Pairable.ratingToPro(9999))   // clamped
        assertEquals(1, Pairable.ratingToPro(-9999))  // clamped to MIN_PRO
    }

    @Test
    fun `016 proToRating round-trip`() {
        for (p in Pairable.MIN_PRO..Pairable.MAX_PRO) {
            assertEquals(p, Pairable.ratingToPro(Pairable.proToRating(p)))
        }
        assertEquals(2700, Pairable.proToRating(1))
        assertEquals(2940, Pairable.proToRating(9))
    }

    @Test
    fun `017 parseRankAndPro handles amateur and pro`() {
        assertEquals(Pair(0, 0), Pairable.parseRankAndPro("1d"))
        assertEquals(Pair(-1, 0), Pairable.parseRankAndPro("1k"))
        assertEquals(Pair(-30, 0), Pairable.parseRankAndPro("30k"))
        // 1p (2700) → rank 6 (=7d strength) + pro=1
        assertEquals(Pair(6, 1), Pairable.parseRankAndPro("1p"))
        assertEquals(Pair(6, 2), Pairable.parseRankAndPro("2p"))   // 2730 → 7d (still in 7d bucket)
        assertEquals(Pair(8, 9), Pairable.parseRankAndPro("9p"))   // 2940 → 9d (clamped)
        assertEquals(Pair(6, 1), Pairable.parseRankAndPro("1P"))   // case-insensitive
        assertNull(Pairable.parseRankAndPro("0p"))
        assertNull(Pairable.parseRankAndPro("10p"))
        assertNull(Pairable.parseRankAndPro("foo"))
        assertNull(Pairable.parseRankAndPro(""))
    }

    @Test
    fun `018 displayRank with pro overrides amateur`() {
        assertEquals("1d", displayRank(0, 0))
        assertEquals("1d", displayRank(0))
        assertEquals("1k", displayRank(-1, 0))
        assertEquals("1p", displayRank(6, 1))      // pro takes precedence
        assertEquals("9p", displayRank(8, 9))
        assertEquals("1d", displayRank(0, 0))
    }

    @Test
    fun `019 Player JSON round-trip with pro`() {
        val original = Json.MutableObject(
            "id" to 42,
            "name" to "Cho",
            "firstname" to "Chikun",
            "rating" to 2820,
            "rank" to 7,           // 8d-equivalent
            "country" to "JP",
            "club" to "xxxx",
            "final" to true,
            "pro" to 5
        )
        val player = Player.fromJson(original)
        assertEquals(5, player.pro)
        assertEquals(7, player.rank)
        // Pairable.displayRank() extension factors in pro for Players
        assertEquals("5p", (player as Pairable).displayRank())
        val roundTrip = player.toJson()
        assertEquals(5, roundTrip.getInt("pro"))
        assertEquals(7, roundTrip.getInt("rank"))
        assertEquals(2820, roundTrip.getInt("rating"))
    }

    @Test
    fun `020 Player without pro omits the field in JSON`() {
        val original = Json.MutableObject(
            "id" to 1,
            "name" to "Doe",
            "firstname" to "John",
            "rating" to 2050,
            "rank" to 0,
            "country" to "FR",
            "club" to "xxxx",
            "final" to true
        )
        val player = Player.fromJson(original)
        assertEquals(0, player.pro)
        val json = player.toJson()
        assertNull(json.getInt("pro"))
    }

    @Test
    fun `001 test detRandom`() {

        fun detRandomCopy(p1:String, p2:String):Double{
            var name1 = p1
            var name2 = p2
            if (name1 > name2) {
                name1 = name2.also { name2 = name1 }
            }
            val s = "$name1$name2"
            var nR = s.mapIndexed { i, c ->
                c.code.toDouble() * (i + 1)
            }.sum()
/*            logger.info("nR = "+nR.toString())
            var i = 0
            nR = 0.0
            for (i in 0..s.length-1) {
                nR += s[i].code.toDouble()*(i+1)
                logger.info(i.toString()+" "+s[i]+" "+nR)
            }
            logger.info("nR for string "+"$name1$name2"+" "+nR.toString())*/
            return nR
        }

        var name1 = "MizessynFrançois"
        var name2 = "BonisMichel"

        assertEquals(42923.0, detRandomCopy(name1, name2))

/*        nR = nR * 1234567 % (max + 1)
        if (inverse) nR = max - nR
        assertEquals(1.0, nR)*/
    }

}
