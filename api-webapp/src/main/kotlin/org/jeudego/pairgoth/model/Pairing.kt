package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.MainCritParams.SeedMethod.SPLIT_AND_SLIP
import org.jeudego.pairgoth.model.PairingType.*
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import org.jeudego.pairgoth.pairing.solver.SwissSolver

// base pairing parameters
data class BaseCritParams(
    // standard NX1 factor for concavity curves
    val nx1: Double = 0.5,
    val dupWeight: Double = MAX_AVOIDDUPGAME,
    val random: Double = 0.0,
    val deterministic: Boolean = true,
    val colorBalanceWeight: Double = MAX_COLOR_BALANCE,
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
        val default = BaseCritParams()
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
    val lastRoundForSeedSystem1: Int = 1,
    val seedSystem1: SeedMethod = SeedMethod.SPLIT_AND_RANDOM,
    val seedSystem2: SeedMethod = SeedMethod.SPLIT_AND_FOLD,
    val additionalPlacementCritSystem1: Criterion = Criterion.RATING,
    val additionalPlacementCritSystem2: Criterion = Criterion.NONE,
) {
    enum class DrawUpDown { TOP, MIDDLE, BOTTOM }
    enum class SeedMethod { SPLIT_AND_FOLD, SPLIT_AND_RANDOM, SPLIT_AND_SLIP }
    companion object {
        const val MAX_CATEGORIES_WEIGHT = 20000000000000.0 // 2e13
        // Ratio between MAX_SCORE_WEIGHT and MAX_CATEGORIES_WEIGHT should stay below 1/ nbcat^2
        const val MAX_SCORE_WEIGHT = 100000000000.0 // 1e11
        const val MAX_DRAW_UP_DOWN_WEIGHT = MAX_SCORE_WEIGHT / 1000.0;  // Draw-ups Draw-downs
        const val MAX_SEEDING_WEIGHT = MAX_SCORE_WEIGHT / 20000.0;
        val default = MainCritParams()
    }
}

// secondary criterium parameters
data class SecondaryCritParams(
    val barThresholdActive: Boolean = true, // Do not apply secondary criteria for players above bar
    val rankThreshold: Int = 0, // Do not apply secondary criteria above 1D rank
    val nbWinsThresholdActive: Boolean = true, // Do not apply secondary criteria when nbWins >= nbRounds / 2
    val defSecCrit: Double = MainCritParams.MAX_CATEGORIES_WEIGHT, // Should be MA_MAX_MINIMIZE_SCORE_DIFFERENCE for MM, MA_MAX_AVOID_MIXING_CATEGORIES for others
) {
    companion object {
        val default = SecondaryCritParams()
    }
}

// geographical pairing params
data class GeographicalParams(
    val avoidSameGeo: Double = 0.0, // Should be SecondaryCritParams.defSecCrit for SwCat and MM, 0 for Swiss
    val preferMMSDiffRatherThanSameCountry: Int = 1,    // Typically = 1
    val preferMMSDiffRatherThanSameClubsGroup: Int = 2, // Typically = 2
    val preferMMSDiffRatherThanSameClub: Int = 3,       // Typically = 3
) {
    companion object {
        val disabled = GeographicalParams(avoidSameGeo = 0.0)
    }
}

// handicap params
data class HandicapParams(
    // minimizeHandicap is a secondary criteria but moved here
    val weight: Double = 0.0, // Should be paiSeDefSecCrit for SwCat, 0 for others
    val useMMS: Boolean = true, // if useMMS is false, hd will be based on rank
    // When one player in the game has a rank of at least hdNoHdRankThreshold,
    // then the game will be without handicap
    val rankThreshold: Int = 0, // 0 is 1d
    val correction: Int = 1, // Handicap will be decreased by hdCorrection
    val ceiling: Int = 9, // Possible values are between 0 and 9
) {
    companion object {
        val default = HandicapParams(
            weight = 0.0, // default disables handicap
            useMMS = false,
            rankThreshold = -30, // 30k
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
    abstract fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game>
}

private fun Tournament<*>.historyBefore(round: Int) =
    if (lastRound() == 1) emptyList()
    else (1 until round).map { games(it).values.toList() }

/*private fun Tournament<*>.historyBefore(round: Int) : List<List<Game>> {
    println("Welcome to tournament.historyBefore !")
    println("lastround and round = "+lastRound().toString()+"   "+round.toString())
    println((1 until round).map { it })
    println((1 until round).map { games(it).values.toList() })
    if (lastRound() == 1){
        return emptyList()
    }
    else {
        return (1 until round).map { games(it).values.toList() }
    }
}*/

class Swiss(
    pairingParams: PairingParams = PairingParams(
        base = BaseCritParams(),
        main = MainCritParams(
            seedSystem1 = SPLIT_AND_SLIP,
            seedSystem2 = SPLIT_AND_SLIP
        ),
        secondary = SecondaryCritParams(
            barThresholdActive = true,
            rankThreshold = -30,
            nbWinsThresholdActive = true,
            defSecCrit = MainCritParams.MAX_CATEGORIES_WEIGHT
        ),
        geo = GeographicalParams.disabled,
        handicap = HandicapParams.default
    ),
    placementParams: PlacementParams = PlacementParams(
        Criterion.NBW, Criterion.SOSW, Criterion.SOSOSW
    )
): Pairing(SWISS, pairingParams, placementParams) {
    companion object {}
    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        return SwissSolver(round, tournament.historyBefore(round), pairables, pairingParams, placementParams).pair()
    }
}

class MacMahon(
    pairingParams: PairingParams = PairingParams(
        base = BaseCritParams(),
        main = MainCritParams(),
        secondary = SecondaryCritParams(
            defSecCrit = MainCritParams.MAX_SCORE_WEIGHT
        ),
        geo = GeographicalParams(
            avoidSameGeo = MainCritParams.MAX_SCORE_WEIGHT
        ),
        handicap = HandicapParams()
    ),
    placementParams: PlacementParams = PlacementParams(
        Criterion.NBW, Criterion.SOSW, Criterion.SOSOSW
    ),
    var mmFloor: Int = -20, // 20k
    var mmBar: Int = 0 // 1D
): Pairing(MAC_MAHON, pairingParams, placementParams) {
    companion object {}
    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        return MacMahonSolver(round, tournament.historyBefore(round), pairables, pairingParams, placementParams, mmFloor, mmBar).pair()
    }
}

class RoundRobin(
    pairingParams: PairingParams = PairingParams(),
    placementParams: PlacementParams = PlacementParams(Criterion.NBW, Criterion.RATING)
): Pairing(ROUND_ROBIN, pairingParams, placementParams) {
    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        TODO()
    }
}

// Serialization

fun BaseCritParams.Companion.fromJson(json: Json.Object) = BaseCritParams(
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
    "colorBalanceWeight" to colorBalanceWeight
)

fun MainCritParams.Companion.fromJson(json: Json.Object) = MainCritParams(
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
    additionalPlacementCritSystem2 = json.getString("secondSeedAddCrit")?.let { Criterion.valueOf(it) } ?: default.additionalPlacementCritSystem2
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
    "secondSeedAddCrit" to additionalPlacementCritSystem2
)

fun SecondaryCritParams.Companion.fromJson(json: Json.Object) = SecondaryCritParams(
    barThresholdActive = json.getBoolean("barTreshold") ?: default.barThresholdActive,
    rankThreshold = json.getInt("rankTreshold") ?: default.rankThreshold,
    nbWinsThresholdActive = json.getBoolean("winsTreshold") ?: default.nbWinsThresholdActive,
    defSecCrit = json.getDouble("secWeight") ?: default.defSecCrit
)

fun SecondaryCritParams.toJson() = Json.Object(
    "barTreshold" to barThresholdActive,
    "rankTreshold" to rankThreshold,
    "winsTreshold" to nbWinsThresholdActive,
    "secWeight" to defSecCrit
)

fun GeographicalParams.Companion.fromJson(json: Json.Object) = GeographicalParams(
    avoidSameGeo = json.getDouble("weight") ?: disabled.avoidSameGeo,
    preferMMSDiffRatherThanSameCountry = json.getInt("mmsDiffCountry") ?: disabled.preferMMSDiffRatherThanSameCountry,
    preferMMSDiffRatherThanSameClubsGroup = json.getInt("mmsDiffClubGroup") ?: disabled.preferMMSDiffRatherThanSameClubsGroup,
    preferMMSDiffRatherThanSameClub = json.getInt("mmsDiffClub") ?: disabled.preferMMSDiffRatherThanSameClub
)

fun GeographicalParams.toJson() = Json.Object(
    "weight" to avoidSameGeo,
    "mmsDiffCountry" to preferMMSDiffRatherThanSameCountry,
    "mmsDiffClubGroup" to preferMMSDiffRatherThanSameClubsGroup,
    "mmsDiffClub" to preferMMSDiffRatherThanSameClub
)

fun HandicapParams.Companion.fromJson(json: Json.Object) = HandicapParams(
    weight = json.getDouble("weight") ?: default.weight,
    useMMS = json.getBoolean("useMMS") ?: default.useMMS,
    rankThreshold = json.getInt("treshold") ?: default.rankThreshold,
    correction = json.getInt("correction") ?: default.correction,
    ceiling = json.getInt("ceiling") ?: default.ceiling
)

fun HandicapParams.toJson() = Json.Object(
    "weight" to weight,
    "useMMS" to useMMS,
    "treshold" to rankThreshold,
    "correction" to correction,
    "ceiling" to ceiling
)

fun Pairing.Companion.fromJson(json: Json.Object): Pairing {
    // get default values for each type
    val type = json.getString("type")?.let { PairingType.valueOf(it) } ?: badRequest("missing pairing type")
    val defaultParams = when (type) {
        SWISS -> Swiss()
        MAC_MAHON -> MacMahon()
        ROUND_ROBIN -> RoundRobin()
    }
    val base = json.getObject("base")?.let { BaseCritParams.fromJson(it) } ?: defaultParams.pairingParams.base
    val main = json.getObject("main")?.let { MainCritParams.fromJson(it) } ?: defaultParams.pairingParams.main
    val secondary = json.getObject("secondary")?.let { SecondaryCritParams.fromJson(it) } ?: defaultParams.pairingParams.secondary
    val geo = json.getObject("geo")?.let { GeographicalParams.fromJson(it) } ?: defaultParams.pairingParams.geo
    val hd = json.getObject("handicap")?.let { HandicapParams.fromJson(it) } ?: defaultParams.pairingParams.handicap
    val pairingParams = PairingParams(base, main, secondary, geo, hd)
    val placementParams = json.getArray("placement")?.let { PlacementParams.fromJson(it) } ?: defaultParams.placementParams
    return when (type) {
        SWISS -> Swiss(pairingParams, placementParams)
        MAC_MAHON -> MacMahon(pairingParams, placementParams).also { mm ->
            mm.mmFloor = json.getInt("mmFloor") ?: -20
            mm.mmBar = json.getInt("mmBar") ?: 0
        }
        ROUND_ROBIN -> RoundRobin(pairingParams, placementParams)
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
    if (this is MacMahon) {
        ret["mmFloor"] = mmFloor
        ret["mmBar"] = mmBar
    }
}
