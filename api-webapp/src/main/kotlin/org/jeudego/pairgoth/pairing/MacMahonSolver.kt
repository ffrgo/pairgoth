package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Pairing
import org.jeudego.pairgoth.model.Swiss.Method.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

class MacMahonSolver(history: List<Game>, pairables: List<Pairable>, weights: Pairing.Weights, val mmBase: Int, val mmBar: Int, val reducer: Int): Solver(history, pairables, weights) {

    val Pairable.mms get() = mmBase + score

    // CB TODO - configurable criteria
    override fun sort(p: Pairable, q: Pairable): Int =
        if (p.mms != q.mms) ((q.mms - p.mms) * 1000).toInt()
        else if (p.sos != q.sos) ((q.sos - p.sos) * 1000).toInt()
        else if (p.sosos != q.sosos) ((q.sosos - p.sosos) * 1000).toInt()
        else 0

    override fun weight(black: Pairable, white: Pairable): Double {
        var weight = 0.0
        if (black.played(white)) weight += weights.played
        if (black.club == white.club) weight += weights.club
        if (black.country == white.country) weight += weights.country
        weight += (abs(black.colorBalance + 1) + abs(white.colorBalance - 1)) * weights.color

        // MacMahon specific
        weight += Math.abs(black.mms - white.mms) * weights.score
        if (sign(mmBar - black.mms) != sign(mmBar - white.mms)) weight += weights.group

        if (black.mms < mmBar && white.mms < mmBar && abs(black.mms - white.mms) > reducer) {
            if (black.mms > white.mms) weight = Double.NaN
            else weight = handicap(black, white) * weights.handicap
        }
        return weight
    }

    override fun handicap(black: Pairable, white: Pairable) =
        if (black.mms > mmBar || white.mms > mmBar || abs(black.mms - white.mms) < reducer || black.mms > white.mms) 0
        else (white.mms - black.mms - reducer).roundToInt()

}
