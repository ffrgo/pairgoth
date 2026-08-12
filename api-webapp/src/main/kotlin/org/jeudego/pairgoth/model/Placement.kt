package org.jeudego.pairgoth.model

import com.republicate.kson.Json

enum class Criterion {
    NONE, // No ranking / tie-break

    CATEGORY,
    RANK,
    RATING,
    NBW, // Number win
    MMS, // Macmahon score
    STS, // Strasbourg score
    CPS, // Cup score
    SCOREX, // CB TODO - I'm adding this one for the congress, didn't find its name in OG after a quick check, needs a deeper investigation
    BDW, // Number of board wins (team tournaments), OpenGotha's TPL_CRIT_BOARDWINS

    SOSW, // Sum of opponents NBW
    SOSWM1, //-1
    SOSWM2, //-2
    SODOSW, // Sum of defeated opponents NBW
    SOSOSW, // Sum of opponents SOSW
    CUSSW, // Cumulative sum of scores (NBW)

    SOSM, // Sum of opponents McMahon score
    SOSMM1, // Same as previous group but with McMahon score
    SOSMM2,
    SODOSM,
    SOSOSM,
    CUSSM,

    SOSTS, // Sum of opponnents Strasbourg score

    EXT, // Exploits tentes
    EXR, // Exploits reussis

    // For the three criteria below see the user documentation
    SDC, // Simplified direct confrontation
    DC, // Direct confrontation
    EGFDC, // Direct comparison, as defined by the EGF tournament system rules

    PREV, // Previous order: the players' relative order at an earlier time
    LOTTERY, // Drawing of lots, the EGF's last resort tie-break
}

/**
 * EGF "Lottery": one lot for each of the tied players, drawn in order. The standings are
 * recomputed at every request, so the draw is a stable hash of the player id (splitmix64
 * finalizer) rather than a live draw — same players, same lots, for the whole tournament.
 * Values spread over [0, 100) by thousandths.
 */
fun lotteryValue(id: ID): Double {
    var h = id.toLong() * -7046029254386353131L
    h = (h xor (h ushr 30)) * -4658895280553007687L
    h = (h xor (h ushr 27)) * -7723592293110705685L
    h = h xor (h ushr 31)
    return Math.floorMod(h, 100000L).toDouble() / 1000.0
}

/**
 * EGF "Previous Order": the players' relative order at a specified earlier time (a qualification,
 * a previous tournament), 1 being the best. A criterion is better when greater, hence the negated
 * order; players with no recorded order rank behind every player that has one.
 */
fun previousOrderValue(pairable: Pairable) = pairable.previousOrder?.let { -it.toDouble() } ?: -1000000.0

class PlacementParams(vararg crit: Criterion) {
    companion object {}

    val criteria = crit.toList().also {
        check()
    }

    private fun check() {
        // throws an exception if criteria are incoherent
        // TODO - if (not coherent) throw Error("...")
    }
}

fun PlacementParams.Companion.fromJson(json: Json.Array) = PlacementParams(*json.map {
    Criterion.valueOf(it!! as String)
}.toTypedArray())

fun PlacementParams.toJson() = Json.Array(*criteria.map {
    it.name
}.toTypedArray())
