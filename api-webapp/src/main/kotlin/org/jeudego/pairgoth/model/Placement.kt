package org.jeudego.pairgoth.model

enum class PlacementCriterion {
    NULL, // No ranking/tie-break

    CATEGORY,
    RANK,
    RATING,
    NBW, // Number win
    MMS, // Macmahon score
    STS, // Strasbourg score
    CPS, // Cup score

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

class PlacementParams(vararg criteria: PlacementCriterion) {
    companion object {
        const val MAX_NUMBER_OF_CRITERIA: Int = 6
    }

    private fun addNullCriteria(criteria: Array<out PlacementCriterion>): ArrayList<PlacementCriterion> {
        var criteria = arrayListOf(*criteria)
        while (criteria.size < MAX_NUMBER_OF_CRITERIA) {
            criteria.add(PlacementCriterion.NULL)
        }
        return criteria
    }

    val criteria = addNullCriteria(criteria)

    open fun checkWarnings(): String {
        // Returns a warning message if criteria are incoherent
        // TODO
        return ""
    }
}