package org.jeudego.pairgoth.pairing

import org.jeudego.pairgoth.model.*
import java.util.*

abstract class BasePairingHelper(
    history: List<List<Game>>, // History of all games played for each round
    var pairables: List<Pairable>, // All pairables for this round, it may include the bye player
    val pairing: PairingParams,
    val placement: PlacementParams,
    ) {

    abstract val scores: Map<ID, Double>
    val historyHelper = if (pairables.first().let { it is TeamTournament.Team && it.teamOfIndividuals }) TeamOfIndividualsHistoryHelper(history) { scores }
    else HistoryHelper(history) { scores }

    // Extend pairables with members from all rounds

    // The main criterion that will be used to define the groups should be defined by subclasses
    // SOS and variants will be computed based on this score
    val Pairable.main: Double get() = scores[id] ?: 0.0
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

    protected val pairablesMap by lazy {
        pairables.associateBy { it.id }
    }

    // Generic parameters calculation
    //private val standingScore by lazy { computeStandingScore() }

    // Decide each pairable group based on the main criterion
    protected val groupsCount get() = 1 + (mainLimits.second - mainLimits.first).toInt()
    private val _groups by lazy {
        pairables.associate { pairable -> Pair(pairable.id, pairable.main.toInt()) }
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

    // already paired players map
    protected fun Pairable.played(other: Pairable) = historyHelper.playedTogether(this, other)

    // color balance (nw - nb)
    protected val Pairable.colorBalance: Int get() = historyHelper.colorBalance(this) ?: 0

    protected val Pairable.group: Int get() = _groups[id]!!

    protected val Pairable.drawnUpDown: Pair<Int,Int> get() = historyHelper.drawnUpDown(this) ?: Pair(0,0)

    protected val Pairable.nbBye: Int get() = historyHelper.nbPlayedWithBye(this) ?: 0

    // score (number of wins)
    val Pairable.nbW: Double get() = historyHelper.nbW(this) ?: 0.0

    val Pairable.sos: Double get() = historyHelper.sos[id] ?: 0.0
    val Pairable.sosm1: Double get() = historyHelper.sosm1[id] ?: 0.0
    val Pairable.sosm2: Double get() = historyHelper.sosm2[id] ?: 0.0
    val Pairable.sosos: Double get() = historyHelper.sosos[id] ?: 0.0
    val Pairable.sodos: Double get() = historyHelper.sodos[id] ?: 0.0
    val Pairable.cums: Double get() = historyHelper.cumScore[id] ?: 0.0
    fun Pairable.missedRounds(upToRound: Int): Int = (1..upToRound).map { round ->
        if (historyHelper.playersPerRound.getOrNull(round - 1)?.contains(id) == true) 0 else 1
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
                return (criterionQ * 1e6 - criterionP * 1e6).toInt()
            }
        }
        if (p.rating == q.rating) {
            return if (p.name > q.name) 1 else -1
        }
        return q.rating - p.rating
    }
    open fun nameSort(p: Pairable, q: Pairable): Int {
        return if (p.name > q.name) 1 else -1
    }
}