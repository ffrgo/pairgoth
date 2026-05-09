package org.jeudego.pairgoth.test

import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.displayRank
import org.jeudego.pairgoth.model.parseRank
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
