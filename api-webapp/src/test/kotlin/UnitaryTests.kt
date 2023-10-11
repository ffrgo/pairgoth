package org.jeudego.pairgoth.test

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class UnitaryTests: TestBase() {

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
