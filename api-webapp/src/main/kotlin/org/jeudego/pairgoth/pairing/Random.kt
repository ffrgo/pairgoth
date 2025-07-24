package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Pairable

fun detRandom(max: Double, p1: Pairable, p2: Pairable, symmetric: Boolean): Double {
    var inverse = false
    var name1 = p1.fullName("")
    var name2 = p2.fullName("")
    if (name1 > name2) {
        name1 = name2.also { name2 = name1 }
        inverse = true
    }
    var nR = "$name1$name2".mapIndexed { i, c ->
        c.code.toDouble() * (i + 1)
    }.sum() * 1234567 % (max + 1)
    // we want the symmetry, except when explicitly asked for a legacy asymmetric detRandom, for tests
    // if (inverse && BaseSolver.asymmetricDetRandom) {
    if (inverse && !symmetric) {
        nR = max - nR
    }
    return nR
}

fun nonDetRandom(max: Double) =
    if (max == 0.0) 0.0
    else Math.random() * (max + 1.0)