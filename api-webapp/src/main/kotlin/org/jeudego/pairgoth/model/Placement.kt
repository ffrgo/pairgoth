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

    SOSW, // Sum of opponents NBW
    SOSWM1, //-1
    SOSWM2, //-2
    SODOSW, // Sum of defeated opponents NBW
    SOSOSW, // Sum of opponenent SOS
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

    // For the two criteria below see the user documentation
    SDC, // Simplified direct confrontation
    DC, // Direct confrontation
}

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
