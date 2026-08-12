package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.MainCritParams.SeedMethod.SPLIT_AND_SLIP
import org.jeudego.pairgoth.model.PairingType.*
import org.jeudego.pairgoth.pairing.HistoryHelper
import org.jeudego.pairgoth.pairing.solver.Solver
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import org.jeudego.pairgoth.pairing.solver.PairingListener
import org.jeudego.pairgoth.pairing.solver.SwissSolver
import kotlin.math.min

// base pairing parameters
data class BaseCritParams(
    // standard NX1 factor for concavity curves
    val nx1: Double = 0.5,
    val dupWeight: Double = MAX_AVOIDDUPGAME,
    val random: Double = 0.0,
    val deterministic: Boolean = true,
    val colorBalanceWeight: Double = MAX_COLOR_BALANCE/1000, // reduce default value to 1e3 (to avoid split bug)
    val byeWeight: Double = MAX_BYE_WEIGHT // This weight is not in opengotha
) {
    init {
        if (nx1 < 0.0 || nx1 > 1.0) throw Error("invalid standardNX1Factor")
        if (dupWeight < 0.0 || dupWeight > MAX_AVOIDDUPGAME) throw Error("invalid avoidDuplGame value")
        if (random < 0.0 || random > MAX_RANDOM) throw Error("invalid random")
        if (colorBalanceWeight < 0.0 || colorBalanceWeight > MAX_COLOR_BALANCE) throw Error("invalid ColorBalanceWeight")
    }

    companion object {
        const val MAX_AVOIDDUPGAME = 500000000000000.0 // 5e14
        const val MAX_BYE_WEIGHT = 100000000000.0 // 1e11
        const val MAX_RANDOM = 1000000000.0 // 1e9
        const val MAX_COLOR_BALANCE = 1000000.0 // 1e6
    }
}

// main criterium parameters
data class MainCritParams(
    // TB - TODO move avoidmixingcategories to swiss with category?
    val categoriesWeight: Double = MAX_CATEGORIES_WEIGHT, // opengotha avoidMixingCategories
    val scoreWeight: Double = MAX_SCORE_WEIGHT, // opengotha minimizeScoreDifference
    val drawUpDownWeight: Double = MAX_DRAW_UP_DOWN_WEIGHT, // opengotha DUDDWeight
    val compensateDrawUpDown: Boolean = true,
    val drawUpDownUpperMode: DrawUpDown = DrawUpDown.MIDDLE,
    val drawUpDownLowerMode: DrawUpDown = DrawUpDown.MIDDLE,
    val seedingWeight: Double = MAX_SEEDING_WEIGHT, // 5 *10^6, opengotha maximizeSeeding
    val lastRoundForSeedSystem1: Int = 2,
    val seedSystem1: SeedMethod = SeedMethod.SPLIT_AND_RANDOM,
    val seedSystem2: SeedMethod = SeedMethod.SPLIT_AND_FOLD,
    val additionalPlacementCritSystem1: Criterion = Criterion.RATING,
    val additionalPlacementCritSystem2: Criterion = Criterion.NONE,
    val mmsValueAbsent: Double = 0.5,
    val nbwValueAbsent: Double = 0.0, // the EGF gives ½ to a player who does not play a round, if the tournament rules agree
    val roundDownScore: Boolean = true,
    val sosValueAbsentUseBase: Boolean = true
) {
    enum class DrawUpDown { TOP, MIDDLE, BOTTOM }
    enum class SeedMethod { SPLIT_AND_FOLD, SPLIT_AND_RANDOM, SPLIT_AND_SLIP }
    companion object {
        const val MAX_CATEGORIES_WEIGHT = 20000000000000.0 // 2e13
        // Ratio between MAX_SCORE_WEIGHT and MAX_CATEGORIES_WEIGHT should stay below 1/ nbcat^2
        const val MAX_SCORE_WEIGHT = 100000000000.0 // 1e11
        const val MAX_DRAW_UP_DOWN_WEIGHT = MAX_SCORE_WEIGHT / 1000.0;  // Draw-ups Draw-downs
        const val MAX_SEEDING_WEIGHT = MAX_SCORE_WEIGHT / 20000.0;
    }
}

// secondary criterium parameters
data class SecondaryCritParams(
    val barThresholdActive: Boolean = true, // Do not apply secondary criteria for players above bar
    val rankSecThreshold: Int = 0, // Do not apply secondary criteria above 1D rank
    val nbWinsThresholdActive: Boolean = true, // Do not apply secondary criteria when nbWins >= nbRounds / 2
    val defSecCrit: Double = MainCritParams.MAX_CATEGORIES_WEIGHT, // Should be MA_MAX_MINIMIZE_SCORE_DIFFERENCE for MM, MA_MAX_AVOID_MIXING_CATEGORIES for others
) {
    companion object {}
}

// geographical pairing params
data class GeographicalParams(
    val avoidSameGeo: Double = 0.0, // Should be SecondaryCritParams.defSecCrit for SwCat and MM, 0 for Swiss
    val preferMMSDiffRatherThanSameCountry: Int = 1,    // Typically = 1
    val preferMMSDiffRatherThanSameClubsGroup: Int = 2, // Typically = 2
    val preferMMSDiffRatherThanSameClub: Int = 3,       // Typically = 3
    // Main-club adjustment: when enabled, a "main club" is detected as the club whose
    // members exceed mainClubDetectionThreshold of the field. The same-club avoidance
    // is then relaxed between main-club members (sensible when 40%+ are from the host
    // club) and the country-factor gate is dropped for dominant-country events.
    // Disabled by default to keep stock behavior aligned with OpenGotha.
    val mainClubAdjustment: Boolean = false,
    val mainClubDetectionThreshold: Double = 0.4,
    val avoidSameFamily: Boolean = false, // Avoid pairing players from the same club with the same family name
) {
    companion object {
        val disabled = GeographicalParams(avoidSameGeo = 0.0)
    }
}

// handicap params
data class HandicapParams(
    // minimizeHandicap is a secondary criteria but moved here
    val weight: Double = 0.0, // "Should be paiSeDefSecCrit for SwCat, 0 for others" ; unused for now, swiss with cats not implemented
    val useMMS: Boolean = true, // if useMMS is false, hd will be based on rank
    // Maximum rank used to compute handicap is rankThreshold
    val rankThreshold: Int = 0, // No handicap if at least one player is above this rank (0 is 1d)
    val correction: Int = 1, // Handicap will be decreased by hdCorrection
    val ceiling: Int = 9, // Possible values are between 0 and 9
) {
    companion object {
        val swissDefault = HandicapParams(
            weight = 0.0,
            useMMS = false,
            rankThreshold = -30, // 30k
            correction = 0,
            ceiling = 0)
    }
}

enum class PairingType { SWISS, MAC_MAHON, ROUND_ROBIN }

data class PairingParams(
    val base: BaseCritParams = BaseCritParams(),
    val main: MainCritParams = MainCritParams(),
    val secondary: SecondaryCritParams = SecondaryCritParams(),
    val geo: GeographicalParams = GeographicalParams(),
    val handicap: HandicapParams = HandicapParams()
)

sealed class Pairing(
    val type: PairingType,
    val pairingParams: PairingParams,
    val placementParams: PlacementParams) {
    companion object {}

    /**
     * Criteria ordering the players *for making pairings*, when they must differ from the ones
     * ordering the final results — "Different tiebreakers might be used for different purposes.
     * Pairing programs should allow such." (EGF tournament system rules; SOS & co are reasonable
     * for pairing and doubtful for the final standings). null = the standings criteria are used.
     */
    var pairingPlacementParams: PlacementParams? = null
    val pairingPlacement: PlacementParams get() = pairingPlacementParams ?: placementParams
    internal abstract fun solver(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): Solver
    internal fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>, legacyMode: Boolean = false, listener: PairingListener? = null): List<Game> {
        val solver = solver(tournament, round, pairables).also { solver ->
            solver.legacyMode = legacyMode
            listener?.let {
                solver.pairingListener = listener
            }
        }
        val games = solver.pair()
        // keep the last batch's enumerator in memory to back "find another optimal pairing"
        solver.enumeration?.let { tournament.repairEnumerations[round] = it }
        return games
    }
}

internal fun Tournament<*>.historyBefore(round: Int) =
    (1 until min(round, lastRound() + 1)).map { games(it).values.toList() }

class Swiss(
    pairingParams: PairingParams = PairingParams(
        base = BaseCritParams(),
        main = MainCritParams(
            seedSystem1 = SPLIT_AND_SLIP,
            seedSystem2 = SPLIT_AND_SLIP,
            additionalPlacementCritSystem2 = Criterion.RATING
        ),
        secondary = SecondaryCritParams(
            barThresholdActive = true,
            rankSecThreshold = -30,
            nbWinsThresholdActive = true,
            defSecCrit = MainCritParams.MAX_CATEGORIES_WEIGHT
        ),
        geo = GeographicalParams.disabled,
        handicap = HandicapParams.swissDefault
    ),
    placementParams: PlacementParams = PlacementParams(
        Criterion.NBW, Criterion.SOSW, Criterion.SOSOSW
    )
): Pairing(SWISS, pairingParams, placementParams) {
    companion object {}
    override fun solver(tournament: Tournament<*>, round: Int, pairables: List<Pairable>) =
        SwissSolver(round, tournament.rounds, HistoryHelper(tournament.historyBefore(round)), pairables, tournament.pairables, pairingParams, pairingPlacement, tournament.usedTables(round))
}

class MacMahon(
    pairingParams: PairingParams = PairingParams(
        base = BaseCritParams(),
        main = MainCritParams(
            seedSystem2 = SPLIT_AND_SLIP // OpenGotha initForMM (the data class default keeps OG's raw pre-preset value)
        ),
        secondary = SecondaryCritParams(
            nbWinsThresholdActive = false, // not relevant in McMahon (OpenGotha initForMM)
            defSecCrit = MainCritParams.MAX_SCORE_WEIGHT
        ),
        geo = GeographicalParams(
            avoidSameGeo = MainCritParams.MAX_SCORE_WEIGHT
        ),
        handicap = HandicapParams( // OpenGotha initForMM
            weight = 0.0,
            useMMS = true,
            rankThreshold = 0, // 1D
            ceiling = 9
        )
    ),
    placementParams: PlacementParams = PlacementParams(
        Criterion.MMS, Criterion.SOSM, Criterion.SOSOSM
    ),
    var mmFloor: Int = -20, // 20k
    var mmBar: Int = 0 // 1D
): Pairing(MAC_MAHON, pairingParams, placementParams) {
    companion object {}
    override fun solver(tournament: Tournament<*>, round: Int, pairables: List<Pairable>) =
        MacMahonSolver(round, tournament.rounds, HistoryHelper(tournament.historyBefore(round)), pairables, tournament.pairables, pairingParams, pairingPlacement, tournament.usedTables(round), mmFloor, mmBar)
}

class RoundRobin(
    pairingParams: PairingParams = PairingParams(),
    placementParams: PlacementParams = PlacementParams(Criterion.NBW, Criterion.RATING)
): Pairing(ROUND_ROBIN, pairingParams, placementParams) {
    override fun solver(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): Solver {
        TODO("not implemented")
    }
}

// Serialization

fun BaseCritParams.Companion.fromJson(json: Json.Object, default: BaseCritParams) = BaseCritParams(
    nx1 = json.getDouble("nx1") ?: default.nx1,
    dupWeight = json.getDouble("dupWeight") ?: default.dupWeight,
    random = json.getDouble("random") ?: default.random,
    deterministic = json.getBoolean("deterministic") ?: default.deterministic,
    colorBalanceWeight = json.getDouble("colorBalanceWeight") ?: default.colorBalanceWeight
)

fun BaseCritParams.toJson() = Json.Object(
    "nx1" to nx1,
    "dupWeight" to dupWeight,
    "random" to random,
    "deterministic" to deterministic,
    "colorBalanceWeight" to colorBalanceWeight
)

fun MainCritParams.Companion.fromJson(json: Json.Object, default: MainCritParams) = MainCritParams(
    categoriesWeight = json.getDouble("catWeight") ?: default.categoriesWeight,
    scoreWeight = json.getDouble("scoreWeight") ?: default.scoreWeight,
    drawUpDownWeight = json.getDouble("upDownWeight") ?: default.drawUpDownWeight,
    compensateDrawUpDown = json.getBoolean("upDownCompensate") ?: default.compensateDrawUpDown,
    drawUpDownLowerMode = json.getString("upDownLowerMode")?.let { MainCritParams.DrawUpDown.valueOf(it) } ?: default.drawUpDownLowerMode,
    drawUpDownUpperMode = json.getString("upDownUpperMode")?.let { MainCritParams.DrawUpDown.valueOf(it) } ?: default.drawUpDownUpperMode,
    seedingWeight = json.getDouble("maximizeSeeding") ?: default.seedingWeight,
    lastRoundForSeedSystem1 = json.getInt("firstSeedLastRound") ?: default.lastRoundForSeedSystem1,
    seedSystem1 = json.getString("firstSeed")?.let { MainCritParams.SeedMethod.valueOf(it) } ?: default.seedSystem1,
    seedSystem2 = json.getString("secondSeed")?.let { MainCritParams.SeedMethod.valueOf(it) } ?: default.seedSystem2,
    additionalPlacementCritSystem1 = json.getString("firstSeedAddCrit")?.let { Criterion.valueOf(it) } ?: default.additionalPlacementCritSystem1,
    additionalPlacementCritSystem2 = json.getString("secondSeedAddCrit")?.let { Criterion.valueOf(it) } ?: default.additionalPlacementCritSystem2,
    mmsValueAbsent = json.getDouble("mmsValueAbsent") ?: default.mmsValueAbsent,
    nbwValueAbsent = json.getDouble("nbwValueAbsent") ?: default.nbwValueAbsent,
    roundDownScore = json.getBoolean("roundDownScore") ?: default.roundDownScore,
    sosValueAbsentUseBase = json.getBoolean("sosValueAbsentUseBase") ?: default.sosValueAbsentUseBase
    )

fun MainCritParams.toJson() = Json.Object(
    "catWeight" to categoriesWeight,
    "scoreWeight" to scoreWeight,
    "upDownWeight" to drawUpDownWeight,
    "upDownCompensate" to compensateDrawUpDown,
    "upDownLowerMode" to drawUpDownLowerMode,
    "upDownUpperMode" to drawUpDownUpperMode,
    "maximizeSeeding" to seedingWeight,
    "firstSeedLastRound" to lastRoundForSeedSystem1,
    "firstSeed" to seedSystem1,
    "secondSeed" to seedSystem2,
    "firstSeedAddCrit" to additionalPlacementCritSystem1,
    "secondSeedAddCrit" to additionalPlacementCritSystem2,
    "mmsValueAbsent" to mmsValueAbsent,
    "nbwValueAbsent" to nbwValueAbsent,
    "roundDownScore" to roundDownScore,
    "sosValueAbsentUseBase" to sosValueAbsentUseBase
)

fun SecondaryCritParams.Companion.fromJson(json: Json.Object, default: SecondaryCritParams) = SecondaryCritParams(
    barThresholdActive = json.getBoolean("barThreshold") ?: default.barThresholdActive,
    rankSecThreshold = json.getInt("rankThreshold") ?: default.rankSecThreshold,
    nbWinsThresholdActive = json.getBoolean("winsThreshold") ?: default.nbWinsThresholdActive,
    defSecCrit = json.getDouble("secWeight") ?: default.defSecCrit
)

fun SecondaryCritParams.toJson() = Json.Object(
    "barThreshold" to barThresholdActive,
    "rankThreshold" to rankSecThreshold,
    "winsThreshold" to nbWinsThresholdActive,
    "secWeight" to defSecCrit
)

fun GeographicalParams.Companion.fromJson(json: Json.Object, default: GeographicalParams) = GeographicalParams(
    avoidSameGeo = json.getDouble("weight") ?: default.avoidSameGeo,
    preferMMSDiffRatherThanSameCountry = json.getInt("mmsDiffCountry") ?: default.preferMMSDiffRatherThanSameCountry,
    preferMMSDiffRatherThanSameClubsGroup = json.getInt("mmsDiffClubGroup") ?: default.preferMMSDiffRatherThanSameClubsGroup,
    preferMMSDiffRatherThanSameClub = json.getInt("mmsDiffClub") ?: default.preferMMSDiffRatherThanSameClub,
    mainClubAdjustment = json.getBoolean("mainClubAdjustment") ?: default.mainClubAdjustment,
    mainClubDetectionThreshold = json.getDouble("mainClubDetectionThreshold") ?: default.mainClubDetectionThreshold,
    avoidSameFamily = json.getBoolean("avoidSameFamily") ?: default.avoidSameFamily
)

fun GeographicalParams.toJson() = Json.Object(
    "weight" to avoidSameGeo,
    "mmsDiffCountry" to preferMMSDiffRatherThanSameCountry,
    "mmsDiffClubGroup" to preferMMSDiffRatherThanSameClubsGroup,
    "mmsDiffClub" to preferMMSDiffRatherThanSameClub,
    "mainClubAdjustment" to mainClubAdjustment,
    "mainClubDetectionThreshold" to mainClubDetectionThreshold,
    "avoidSameFamily" to avoidSameFamily
)

fun HandicapParams.Companion.fromJson(json: Json.Object, default: HandicapParams) = HandicapParams(
    weight = json.getDouble("weight") ?: default.weight,
    useMMS = json.getBoolean("useMMS") ?: default.useMMS,
    rankThreshold = json.getInt("threshold") ?: default.rankThreshold,
    correction = json.getInt("correction") ?: default.correction,
    ceiling = json.getInt("ceiling") ?: default.ceiling
)

fun HandicapParams.toJson() = Json.Object(
    "weight" to weight,
    "useMMS" to useMMS,
    "threshold" to rankThreshold,
    "correction" to correction,
    "ceiling" to ceiling
)

fun Pairing.Companion.fromJson(json: Json.Object, default: Pairing?, teams: Boolean = false): Pairing {
    // get default values for each type
    val type = json.getString("type")?.let { PairingType.valueOf(it) } ?: default?.type ?: badRequest("missing pairing type")
    val defaultParams = when (type) {
        SWISS -> Swiss()
        MAC_MAHON -> MacMahon()
        ROUND_ROBIN -> RoundRobin()
    }
    // per-key fallback: the existing tournament's params, else the type-appropriate defaults
    val inherited = default?.pairingParams ?: defaultParams.pairingParams
    val base = json.getObject("base")?.let { BaseCritParams.fromJson(it, inherited.base) } ?: inherited.base
    val main = json.getObject("main")?.let { MainCritParams.fromJson(it, inherited.main) } ?: inherited.main
    val secondary = json.getObject("secondary")?.let { SecondaryCritParams.fromJson(it, inherited.secondary) } ?: inherited.secondary
    val geo = json.getObject("geo")?.let { GeographicalParams.fromJson(it, inherited.geo) } ?: inherited.geo
    val hd = json.getObject("handicap")?.let { HandicapParams.fromJson(it, inherited.handicap) } ?: inherited.handicap
    val pairingParams = PairingParams(base, main, secondary, geo, hd)
    // EGF: in a team tournament the number of board wins is highly meaningful and should be the
    // first tie-break — so it is what new team tournaments start with (four slots, as the UI shows)
    val defaultPlacement =
        if (teams) PlacementParams(*defaultParams.placementParams.criteria.toMutableList()
            .also { it.add(1, Criterion.BDW) }.take(4).toTypedArray())
        else defaultParams.placementParams
    val placementParams = json.getArray("placement")?.let { PlacementParams.fromJson(it) } ?: default?.placementParams ?: defaultPlacement
    // an all-NONE list means "same criteria as the standings", and clears any previous setting
    val pairingPlacement =
        if (json.containsKey("pairingPlacement"))
            json.getArray("pairingPlacement")?.let { PlacementParams.fromJson(it) }
                ?.takeIf { params -> params.criteria.any { it != Criterion.NONE } }
        else default?.pairingPlacementParams
    return when (type) {
        SWISS -> Swiss(pairingParams, placementParams)
        MAC_MAHON -> MacMahon(pairingParams, placementParams).also { mm ->
            mm.mmFloor = json.getInt("mmFloor") ?: (default as? MacMahon)?.mmFloor ?: -20
            mm.mmBar = json.getInt("mmBar") ?: (default as? MacMahon)?.mmBar ?: 0
        }
        ROUND_ROBIN -> RoundRobin(pairingParams, placementParams)
    }.also { pairing ->
        pairing.pairingPlacementParams = pairingPlacement
    }
}

fun Pairing.toJson(): Json.Object = Json.MutableObject(
    "type" to type.name,
    "base" to pairingParams.base.toJson(),
    "main" to pairingParams.main.toJson(),
    "secondary" to pairingParams.secondary.toJson(),
    "geo" to pairingParams.geo.toJson(),
    "handicap" to pairingParams.handicap.toJson(),
    "placement" to placementParams.toJson()
).also { ret ->
    pairingPlacementParams?.let { ret["pairingPlacement"] = it.toJson() }
    if (this is MacMahon) {
        ret["mmFloor"] = mmFloor
        ret["mmBar"] = mmBar
    }
}
