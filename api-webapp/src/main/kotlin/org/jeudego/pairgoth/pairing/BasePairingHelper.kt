package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*

abstract class BasePairingHelper(
    val round: Int,
    val totalRounds: Int,
    val history: HistoryHelper, // Digested history of all games played for each round
    var pairables: List<Pairable>, // All pairables for this round, it may include the bye player
    val pairing: PairingParams,
    val placement: PlacementParams,
    ) {

    // Extend pairables with members from all rounds

    // The main criterion that will be used to define the groups should be defined by subclasses
    // SOS and variants will be computed based on this score
    val Pairable.main: Double get() = score ?: 0.0
    abstract val mainLimits: Pair<Double, Double>

    // pairables sorted using overloadable sort function
    protected val sortedPairables by lazy {
        pairables.sortedWith(::sort)
    }

    // pairables sorted for pairing purposes
    protected val pairingSortedPairables by lazy {
        pairables.sortedWith(::pairingSort).toMutableList()
    }

    // pairables sorted for pairing purposes
    protected val nameSortedPairables by lazy {
        pairables.sortedWith(::nameSort).toMutableList()
    }

    // Generic parameters calculation
    //private val standingScore by lazy { computeStandingScore() }

    // Decide each pairable group based on the main criterion
    protected val groupsCount get() = 1 + (mainLimits.second - mainLimits.first).toInt()
    private val _groups by lazy {
        pairables.associate { pairable -> Pair(pairable.id, (pairable.main * 2).toInt() / 2) }
    }

    // place (among sorted pairables)
    val Pairable.place: Int get() = _place[id]!!
    private val _place by lazy {
        pairingSortedPairables.mapIndexed { index, pairable ->
            Pair(pairable.id, index)
        }.toMap()
    }

    // placeInGroup (of same score) : Pair(place, groupSize)
    protected val Pairable.placeInGroup: Pair<Int, Int> get() = _placeInGroup[id]!!
    private val _placeInGroup by lazy {
        // group by group number
        pairingSortedPairables.groupBy {
            it.group
            // get a list { id { placeInGroup, groupSize } }
        }.values.flatMap { group ->
            group.mapIndexed { index, pairable ->
                Pair(pairable.id, Pair(index, group.size))
            }
            // get a map id -> { placeInGroup, groupSize }
        }.toMap()
    }

    // number of players in the biggest club and the biggest country
    // this can be used to disable geocost if there is a majority of players from the same country or club
    protected val biggestClubSize by lazy {
        pairables.groupingBy { it.club }.eachCount().values.maxOrNull()!!
    }
    protected val biggestCountrySize by lazy {
        pairables.groupingBy { it.club }.eachCount().values.maxOrNull()!!
    }

    // already paired players map
    protected fun Pairable.played(other: Pairable) = history.playedTogether(this, other)

    // color balance (nw - nb)
    protected val Pairable.colorBalance: Int get() = history.colorBalance[id] ?: 0

    protected val Pairable.group: Int get() = _groups[id]!!

    protected val Pairable.drawnUpDown: Pair<Int, Int> get() = history.drawnUpDown[id] ?: Pair(0, 0)

    protected val Pairable.nbBye: Int get() = history.nbPlayedWithBye(this) ?: 0

    val Pairable.score: Double get() = history.scores[id] ?: 0.0
    val Pairable.scoreX: Double get() = history.scoresX[id] ?: 0.0
    val Pairable.nbW: Double get() = history.wins[id] ?: 0.0
    val Pairable.sos: Double get() = history.sos[id] ?: 0.0
    val Pairable.sosm1: Double get() = history.sosm1[id] ?: 0.0
    val Pairable.sosm2: Double get() = history.sosm2[id] ?: 0.0
    val Pairable.sosos: Double get() = history.sosos[id] ?: 0.0
    val Pairable.sodos: Double get() = history.sodos[id] ?: 0.0
    val Pairable.cums: Double get() = history.cumScore[id] ?: 0.0
    fun Pairable.missedRounds(): Int = (1 until round).map { round ->
        if (history.playersPerRound.getOrNull(round - 1)
                ?.contains(id) == true
        ) 0 else 1
    }.sum()

    fun Pairable.eval(criterion: Criterion) = evalCriterion(this, criterion)
    open fun evalCriterion(pairable: Pairable, criterion: Criterion) = when (criterion) {
        Criterion.NONE -> 0.0
        Criterion.CATEGORY -> TODO()
        Criterion.RANK -> pairable.rank.toDouble()
        Criterion.RATING -> pairable.rating.toDouble()
        Criterion.NBW -> pairable.nbW
        Criterion.SOSW -> pairable.sos
        Criterion.SOSWM1 -> pairable.sosm1
        Criterion.SOSWM2 -> pairable.sosm2
        Criterion.SOSOSW -> pairable.sosos
        Criterion.SODOSW -> pairable.sodos
        Criterion.CUSSW -> pairable.cums
        else -> throw Error("criterion cannot be evaluated: ${criterion.name}")
    }

    open fun sort(p: Pairable, q: Pairable): Int {
        for (criterion in placement.criteria) {
            val criterionP = p.eval(criterion)
            val criterionQ = q.eval(criterion)
            if (criterionP != criterionQ) {
                return (criterionQ * 100 - criterionP * 100).toInt()
            }
        }
        return 0
    }

    open fun pairingSort(p: Pairable, q: Pairable): Int {
        for (criterion in placement.criteria) {
            val criterionP = p.eval(criterion)
            val criterionQ = q.eval(criterion)
            if (criterionP != criterionQ) {
                return -criterionP.compareTo(criterionQ)
            }
        }
        val additionalCriterion =
            if (round <= pairing.main.lastRoundForSeedSystem1) pairing.main.additionalPlacementCritSystem1
            else pairing.main.additionalPlacementCritSystem2
        if (additionalCriterion != Criterion.NONE) {
            val criterionP = p.eval(additionalCriterion)
            val criterionQ = q.eval(additionalCriterion)
            if (criterionP != criterionQ) {
                return -criterionP.compareTo(criterionQ)
            }
        }
        return p.fullName().compareTo(q.fullName())
    }

    open fun nameSort(p: Pairable, q: Pairable): Int {
        return p.fullName().compareTo(q.fullName())
    }
}
