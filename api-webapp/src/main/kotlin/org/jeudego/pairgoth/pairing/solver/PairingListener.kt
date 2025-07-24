package org.jeudego.pairgoth.pairing.solver

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Tournament
import java.io.PrintWriter

interface PairingListener {

    fun start(round: Int) {}
    fun startPair(white: Pairable, black: Pairable) {}
    fun endPair(white: Pairable, black: Pairable) {}
    fun addWeight(name: String, weight: Double)
    fun end() {}
}

class LoggingListener(val out: PrintWriter) : PairingListener {

    var currentOpenGothaWeight: Double = 0.0

    override fun start(round: Int) {
        out.println("Round $round")
        out.println("Costs")
    }

    override fun startPair(white: Pairable, black: Pairable) {
        currentOpenGothaWeight = 0.0
        out.println("Player1Name=${white.fullName()}")
        out.println("Player2Name=${black.fullName()}")
    }

    override fun addWeight(name: String, weight: Double) {
        // Try hard to stay in sync with current reference files of OpenGotha conformance tests
        val key = when (name) {
            // TODO - Change to propagate to test reference files
            "baseColorBalance" -> "baseBWBalance"
            // Pairgoth-specific part of the color balance, not considered in conformance tests
            "secColorBalance" -> return
            else -> name
        }
        val value = when (name) {
            // TODO - This cost is always zero in reference files, seems unused
            "secHandi" -> 0.0
            else -> weight
        }
        currentOpenGothaWeight += value
        out.println("${key}Cost=$value")
    }

    override fun endPair(white: Pairable, black: Pairable) {
        out.println("totalCost=$currentOpenGothaWeight")
    }

    override fun end() {
        out.flush()
    }
}

class CollectingListener() : PairingListener {

    val out = mutableMapOf<Pair<ID, ID>, MutableMap<String, Double>>()
    var white: Pairable? = null
    var black: Pairable? = null

    override fun startPair(white: Pairable, black: Pairable) {
        this.white = white
        this.black = black
    }

    override fun addWeight(name: String, weight: Double) {
        val key = Pair(white!!.id, black!!.id)
        val weights = out.computeIfAbsent(key) { mutableMapOf() }
        weights[name] = weight
    }
}
