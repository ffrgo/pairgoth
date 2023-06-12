package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.ext.OpenGothaFormat
import org.jeudego.pairgoth.model.Pairing.PairingType.*
import org.jeudego.pairgoth.pairing.MacMahonSolver
import org.jeudego.pairgoth.pairing.SwissSolver

// Below are some constants imported from opengotha
/**
 * Max value for BaAvoidDuplGame.
 * In order to be compatible with max value of long (8 * 10^18),
 * with max number of games (8000),
 * with relative weight of this parameter (1/2)
 *BA_MAX_AVOIDDUPLGAME should be strictly limited to 5 * 10^14
 */
private const val BA_MAX_AVOIDDUPLGAME: Long = 500000000000000L // 5e14

/**
 * Max value for BaRandom.
 * Due to internal coding,
 * BA_MAX_RANDOM should be strictly limited to 2 * 10^9
 */
private const val BA_MAX_RANDOM: Long = 1000000000L // 2e9
private const val BA_MAX_BALANCEWB: Long = 1000000L // 1e6

private const val MA_MAX_AVOID_MIXING_CATEGORIES: Double = 2e13
// Ratio between MA_MAX_MINIMIZE_SCORE_DIFFERENCE and MA_MAX_AVOID_MIXING_CATEGORIES should stay below 1/ nbcat^2
private const val MA_MAX_MINIMIZE_SCORE_DIFFERENCE: Double = 1e11
private const val MA_MAX_DUDD_WEIGHT: Double = MA_MAX_MINIMIZE_SCORE_DIFFERENCE / 1000;  // Draw-ups Draw-downs
enum class MA_DUDD {TOP, MID, BOT}

private const val MA_MAX_MAXIMIZE_SEEDING: Double = MA_MAX_MINIMIZE_SCORE_DIFFERENCE / 20000;

enum class SeedMethod { SPLIT_AND_FOLD, SPLIT_AND_RANDOM, SPLIT_AND_SLIP }

sealed class Pairing(val type: PairingType, val pairingParams: PairingParams = PairingParams(), val placementParams: PlacementParams) {
    companion object {}
    enum class PairingType { SWISS, MAC_MAHON, ROUND_ROBIN }
    data class PairingParams(
            // Standard NX1 factor ( = Rather N X 1 than 1 X N)
            val standardNX1Factor: Double = 0.5,
            // Base criteria
            val baseAvoidDuplGame: Long = BA_MAX_AVOIDDUPLGAME,
            val baseRandom: Long = 0,
            val baseDeterministic: Boolean = true,
            val baseBalanceWB: Long = BA_MAX_BALANCEWB,

            // Main criteria
            // TODO move avoidmixingcategories to swiss with category
            //val maAvoidMixingCategories: Double = MA_MAX_AVOID_MIXING_CATEGORIES,
            val mainMinimizeScoreDifference: Double = MA_MAX_MINIMIZE_SCORE_DIFFERENCE,

            val maDUDDWeight: Double = MA_MAX_DUDD_WEIGHT,
            val maCompensateDUDD: Boolean = true,
            val maDUDDUpperMode: MA_DUDD = MA_DUDD.MID,
            val maDUDDLowerMode: MA_DUDD = MA_DUDD.MID,

            val maMaximizeSeeding: Double = MA_MAX_MAXIMIZE_SEEDING, // 5 *10^6
            val maLastRoundForSeedSystem1: Int = 1,
            val maSeedSystem1: SeedMethod = SeedMethod.SPLIT_AND_RANDOM,
            val maSeedSystem2: SeedMethod = SeedMethod.SPLIT_AND_FOLD,
            val maAdditionalPlacementCritSystem1: PlacementCriterion = PlacementCriterion.RATING,
            val maAdditionalPlacementCritSystem2: PlacementCriterion = PlacementCriterion.NULL,

            // Secondary criteria
            val seBarThresholdActive: Boolean = true, // Do not apply secondary criteria for players above bar
            val seRankThreshold: Int = 0, // Do not apply secondary criteria above 1D rank
            val seNbWinsThresholdActive: Boolean = true, // Do not apply secondary criteria when nbWins >= nbRounds / 2
            val seDefSecCrit: Double = MA_MAX_AVOID_MIXING_CATEGORIES, // Should be MA_MAX_MINIMIZE_SCORE_DIFFERENCE for MM, MA_MAX_AVOID_MIXING_CATEGORIES for others

            // Geographical params
            val geo: GeographicalParams = GeographicalParams(avoidSameGeo = seDefSecCrit),

            // Handicap related settings
            val hd: HandicapParams = HandicapParams(minimizeHandicap = seDefSecCrit),
    )


    abstract fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game>
}

data class GeographicalParams(
        val avoidSameGeo: Double, // Should be SeDefSecCrit for SwCat and MM, 0 for Swiss
        val preferMMSDiffRatherThanSameCountry: Int = 1,    // Typically = 1
        val preferMMSDiffRatherThanSameClubsGroup: Int = 2, // Typically = 2
        val preferMMSDiffRatherThanSameClub: Int = 3,       // Typically = 3
) {
    companion object {
        fun disabled() = GeographicalParams(avoidSameGeo = 0.0)
    }
}

data class HandicapParams(
        // minimizeHandicap is a secondary criteria but moved here
        val minimizeHandicap: Double, // Should be paiSeDefSecCrit for SwCat, 0 for others
        val basedOnMMS: Boolean = true, // if hdBasedOnMMS is false, hd will be based on rank
        // When one player in the game has a rank of at least hdNoHdRankThreshold,
        // then the game will be without handicap
        val noHdRankThreshold: Int = 0, // 0 is 1d
        val correction: Int = 1, // Handicap will be decreased by hdCorrection
        val ceiling: Int = 9, // Possible values are between 0 and 9
) {
    companion object {
        fun disabled() = HandicapParams(
                minimizeHandicap = 0.0,
                basedOnMMS = false,
                noHdRankThreshold=-30, // 30k
                ceiling=0)
    }
}

fun Tournament<*>.historyBefore(round: Int) =
    if (lastRound() == 0) emptyList()
    else (0 until round).flatMap { games(round).values }

class Swiss(): Pairing(SWISS, PairingParams(
        maSeedSystem1 = SeedMethod.SPLIT_AND_SLIP,
        maSeedSystem2 = SeedMethod.SPLIT_AND_SLIP,

        seBarThresholdActive = true, // not relevant
        seRankThreshold = -30,
        seNbWinsThresholdActive = true, // not relevant
        seDefSecCrit = MA_MAX_AVOID_MIXING_CATEGORIES,

        geo = GeographicalParams.disabled(),
        hd = HandicapParams.disabled(),
), PlacementParams(PlacementCriterion.NBW, PlacementCriterion.SOSW, PlacementCriterion.SOSOSW)) {
    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        return SwissSolver(round, tournament.historyBefore(round), pairables, pairingParams, placementParams).pair()
    }
}

class MacMahon(
    var bar: Int = 0,
    var minLevel: Int = -30,
    var reducer: Int = 1
): Pairing(MAC_MAHON, PairingParams(seDefSecCrit = MA_MAX_MINIMIZE_SCORE_DIFFERENCE),
        PlacementParams(PlacementCriterion.MMS, PlacementCriterion.SOSM, PlacementCriterion.SOSOSM)) {
    val groups = mutableListOf<Int>()

    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        return MacMahonSolver(round, tournament.historyBefore(round), pairables, pairingParams, placementParams, mmBase = minLevel, mmBar = bar, reducer = reducer).pair()
    }
}

class RoundRobin: Pairing(ROUND_ROBIN, PairingParams(), PlacementParams(PlacementCriterion.NBW, PlacementCriterion.RATING)) {
    override fun pair(tournament: Tournament<*>, round: Int, pairables: List<Pairable>): List<Game> {
        TODO()
    }
}

// Serialization
// TODO failing on serialization
fun HandicapParams.toJson() = Json.Object(
    "minimize_hd" to minimizeHandicap,
    "mms_based" to basedOnMMS,
    "no_hd_thresh" to noHdRankThreshold,
    "correction" to correction,
    "ceiling" to ceiling, )

fun HandicapParams.fromJson(json: Json.Object) = HandicapParams(
        minimizeHandicap=json.getDouble("minimize_hd")!!,
        basedOnMMS=json.getBoolean("mms_based")!!,
        noHdRankThreshold=json.getInt("no_hd_thresh")!!,
        correction=json.getInt("correction")!!,
        ceiling=json.getInt("ceiling")!!,
)

fun GeographicalParams.toJson() = Json.Object(
        "avoid_same_geo" to avoidSameGeo,
        "country" to preferMMSDiffRatherThanSameCountry,
        "club_group" to preferMMSDiffRatherThanSameClubsGroup,
        "club" to preferMMSDiffRatherThanSameClub,)

fun GeographicalParams.fromJson(json: Json.Object) = GeographicalParams(
        avoidSameGeo=json.getDouble("avoid_same_geo")!!,
        preferMMSDiffRatherThanSameCountry=json.getInt("country")!!,
        preferMMSDiffRatherThanSameClubsGroup=json.getInt("club_group")!!,
        preferMMSDiffRatherThanSameClub=json.getInt("club")!!,
)


fun Pairing.Companion.fromJson(json: Json.Object) = when (json.getString("type")?.let { Pairing.PairingType.valueOf(it) } ?: badRequest("missing pairing type")) {
    SWISS -> Swiss()
    MAC_MAHON -> MacMahon(
        bar = json.getInt("bar") ?: 0,
        minLevel = json.getInt("minLevel") ?: -30,
        reducer = json.getInt("reducer") ?: 1
    )
    ROUND_ROBIN -> RoundRobin()
}

fun Pairing.toJson() = when (this) {
    is Swiss ->
        Json.Object("type" to type.name, "geo" to pairingParams.geo.toJson(), "hd" to pairingParams.hd.toJson())
    is MacMahon -> Json.Object("type" to type.name, "bar" to bar, "minLevel" to minLevel, "reducer" to reducer)
    is RoundRobin -> Json.Object("type" to type.name)
}

