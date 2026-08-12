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

    val criteria = crit.toList()
}

/**
 * Why a list of criteria cannot work, null if it can. "Only one of SOS-2, SOS-1, or SOS may be
 * used" (EGF tournament system rules) — and the three direct-confrontation flavours are three
 * answers to the same question, so only one of them may be used either.
 *
 * Checked when criteria are *set* (creation and settings update), never when a tournament is
 * loaded: an existing file with an odd list must keep opening.
 */
fun PlacementParams.validate(): String? {
    val used = criteria.filter { it != Criterion.NONE }
    used.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
        return "${it.key.name} is used twice"
    }
    val sosFamily = setOf(
        Criterion.SOSW, Criterion.SOSWM1, Criterion.SOSWM2,
        Criterion.SOSM, Criterion.SOSMM1, Criterion.SOSMM2)
    if (used.count { it in sosFamily } > 1) return "only one of SOS, SOS-1 or SOS-2 may be used"
    val directFamily = setOf(Criterion.DC, Criterion.SDC, Criterion.EGFDC)
    if (used.count { it in directFamily } > 1) return "only one direct confrontation criterion may be used"
    return null
}

fun PlacementParams.Companion.fromJson(json: Json.Array) = PlacementParams(*json.map {
    Criterion.valueOf(it!! as String)
}.toTypedArray())

fun PlacementParams.toJson() = Json.Array(*criteria.map {
    it.name
}.toTypedArray())
