package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
// CB TODO - review
//import kotlinx.datetime.LocalDate
import java.time.LocalDate
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.pairing.solver.MacMahonSolver
import org.jeudego.pairgoth.pairing.solver.SwissSolver
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.store.nextTournamentId
import kotlin.math.max
import java.util.*
import kotlin.math.roundToInt

sealed class Tournament <P: Pairable>(
    val id: ID,
    val type: Type,
    val name: String,
    val shortName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val director: String,
    val country: String,
    val location: String,
    val online: Boolean,
    val timeSystem: TimeSystem,
    val rounds: Int,
    val pairing: Pairing,
    val rules: Rules = Rules.FRENCH,
    val gobanSize: Int = 19,
    val komi: Double = 7.5
) {
    companion object {}
    enum class Type(val playersNumber: Int, val individual: Boolean = true) {
        INDIVIDUAL(1),
        PAIRGO(2, false),
        RENGO2(2, false),
        RENGO3(3, false),
        TEAM2(2),
        TEAM3(3),
        TEAM4(4),
        TEAM5(5);
    }

    // players per id
    abstract val players: MutableMap<ID, Player>

    // pairables per id
    protected val _pairables = mutableMapOf<ID, P>()
    val pairables: Map<ID, Pairable> get() = _pairables

    // pairing
    fun pair(round: Int, pairables: List<Pairable>): List<Game> {
        // Minimal check on round number.
        // CB TODO - the complete check should verify, for each player, that he was either non pairable or implied in the previous round
        if (round > games.size + 1) badRequest("previous round not paired")
        if (round > rounds) badRequest("too many rounds")
        val evenPairables =
            if (pairables.size % 2 == 0) pairables
            else pairables.toMutableList().also { it.add(ByePlayer) }
        return pairing.pair(this, round, evenPairables).also { newGames ->
            if (games.size < round) games.add(mutableMapOf())
            games[round - 1].putAll( newGames.associateBy { it.id } )
        }
    }

    // games per id for each round
    private val games = mutableListOf<MutableMap<ID, Game>>()

    fun games(round: Int) = games.getOrNull(round - 1) ?:
        if (round > games.size + 1) throw Error("invalid round")
        else mutableMapOf<ID, Game>().also { games.add(it) }
    fun lastRound() = max(1, games.size)

    fun recomputeDUDD(round: Int, gameID: ID) {
        // Instantiate solver with game history
        val solver = pairing.solver(this, round, pairables.values.toList())

        // Recomputes DUDD and hd
        val game = games(round)[gameID]!!
        val white = solver.pairables.find { p-> p.id == game.white }!!
        val black = solver.pairables.find { p-> p.id == game.black }!!
        game.drawnUpDown = solver.dudd(black, white)
        game.handicap = solver.hd(white = white, black = black)
    }


    /**
     * Recompute DUDD for the specified round
     */
    fun recomputeDUDD(round: Int) {
        if (pairables.isEmpty() || games(1).isEmpty()) return;
        // Instantiate solver with game history
        val solver = pairing.solver(this, round, pairables.values.toList())
        for (game in games(round).values) {
            if (game.black != 0 && game.white != 0) {
                val white = solver.pairables.find { p-> p.id == game.white }!!
                val black = solver.pairables.find { p-> p.id == game.black }!!
                game.drawnUpDown = solver.dudd(black, white)
            }
        }
    }


    /**
     * Recompute DUDD for all rounds
      */
    fun recomputeDUDD() {
        for (round in 1..rounds) {
            recomputeDUDD(round)
        }
    }

    fun usedTables(round: Int): BitSet =
        games(round).values.map { it.table }.fold(BitSet()) { acc, table ->
            acc.set(table)
            acc
        }

    private fun defaultGameOrderBy(game: Game): Int {
        val whiteRank = pairables[game.white]?.rating ?: Int.MIN_VALUE
        val blackRank = pairables[game.black]?.rating ?: Int.MIN_VALUE
        return -(whiteRank + blackRank)
    }

    fun renumberTables(round: Int, pivot: Game? = null, orderBY: (Game) -> Int = ::defaultGameOrderBy): Boolean {
        var changed = false
        var nextTable = 1
        games(round).values.filter{ game -> pivot?.let { pivot.id != game.id } ?: true }.sortedBy(orderBY).forEach { game ->
            if (pivot != null && nextTable == pivot.table) {
                ++nextTable
            }
            changed = changed || game.table != nextTable
            game.table = nextTable++
        }
        return changed
    }

    fun pairedPlayers() = games.flatMap { it.values }.flatMap { listOf(it.black, it.white) }.toSet()
    fun hasPlayer(dbId: DatabaseId, pId: String) = pId.isNotBlank() && players.values.filter { player -> pId == player.externalIds[dbId] }.isNotEmpty()

    fun stats() = (0..rounds - 1).map { index ->
        Json.Object(
            "participants" to pairables.values.count { !it.skip.contains(index + 1) },
            "paired" to (games.getOrNull(index)?.values?.flatMap { listOf(it.black, it.white) }?.count { it != 0 } ?: 0),
            "games" to (games.getOrNull(index)?.values?.count() ?: 0),
            "ready" to (games.getOrNull(index)?.values?.count { it.result != Game.Result.UNKNOWN } ?: 0)
        )
    }.toJsonArray()
}

// standard tournament of individuals
class StandardTournament(
    id: ID,
    type: Tournament.Type,
    name: String,
    shortName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    director: String,
    country: String,
    location: String,
    online: Boolean,
    timeSystem: TimeSystem,
    rounds: Int,
    pairing: Pairing,
    rules: Rules = Rules.FRENCH,
    gobanSize: Int = 19,
    komi: Double = 7.5
): Tournament<Player>(id, type, name, shortName, startDate, endDate, director, country, location, online, timeSystem, rounds, pairing, rules, gobanSize, komi) {
    override val players get() = _pairables
}

// team tournament
class TeamTournament(
    id: ID,
    type: Tournament.Type,
    name: String,
    shortName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    director: String,
    country: String,
    location: String,
    online: Boolean,
    timeSystem: TimeSystem,
    rounds: Int,
    pairing: Pairing,
    rules: Rules = Rules.FRENCH,
    gobanSize: Int = 19,
    komi: Double = 7.5
): Tournament<TeamTournament.Team>(id, type, name, shortName, startDate, endDate, director, country, location, online, timeSystem, rounds, pairing, rules, gobanSize, komi) {
    companion object {
        private val epsilon = 0.0001
    }
    override val players = mutableMapOf<ID, Player>()
    val teams: MutableMap<ID, Team> = _pairables

    private fun List<Int>.average(provider: (Player)->Int) = ((sumOf {id -> provider(players[id]!!)} - epsilon) / size).roundToInt()

    inner class Team(id: ID, name: String, rating: Int, rank: Int, final: Boolean, mmsCorrection: Int = 0): Pairable(id, name, rating, rank, final, mmsCorrection) {
        val playerIds = mutableSetOf<ID>()
        val teamPlayers: Set<Player> get() = playerIds.mapNotNull { players[it] }.toSet()
        override val club: String? get() = teamPlayers.map { it.club }.distinct().let { if (it.size == 1) it[0] else null }
        override val country: String? get() = teamPlayers.map { it.country }.distinct().let { if (it.size == 1) it[0] else null }
        override fun toMutableJson() = Json.MutableObject(
            "id" to id,
            "name" to name,
            "players" to playerIds.toList().toJsonArray()
        )

        override fun toDetailedJson() = toMutableJson().also { json ->
            json["rank"] = rank
            country?.also { json["country"] = it }
            club?.also { json["club"] = it }
        }
        val teamOfIndividuals: Boolean get() = type.individual

        override val skip get() = playerIds.map { players[it]!!.skip }.reduce { left, right -> (left union right) as MutableSet<Int> }
    }

    fun teamFromJson(json: Json.Object, default: TeamTournament.Team? = null): Team {
        val teamPlayersIds = json.getArray("players")?.let { arr ->
            arr.map {
                if (it != null && it is Number) it.toInt().also { id ->
                    if (!players.containsKey(id)) badRequest("invalid player id: ${id}")
                }
                else badRequest("invalid players array")
            }
        } ?: badRequest("missing players")
        return Team(
            id = json.getInt("id") ?: default?.id ?: nextPlayerId,
            name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
            rating = json.getInt("rating") ?: default?.rating ?: teamPlayersIds.average(Player::rating),
            rank = json.getInt("rank") ?: default?.rank ?: teamPlayersIds.average(Player::rank),
            final = teamPlayersIds.all { players[it]!!.final },
            mmsCorrection = json.getInt("mmsCorrection") ?: default?.mmsCorrection ?: 0
        ).also {
                it.playerIds.addAll(teamPlayersIds)
        }
    }
}

// Serialization

fun Tournament.Companion.fromJson(json: Json.Object, default: Tournament<*>? = null): Tournament<*> {
    val type = json.getString("type")?.uppercase()?.let { Tournament.Type.valueOf(it) } ?: default?.type ?:  badRequest("missing type")
    // No clean way to avoid this redundancy
    val tournament = if (type.playersNumber == 1)
            StandardTournament(
                id = json.getInt("id") ?: default?.id ?: nextTournamentId,
                type = type,
                name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
                shortName = json.getString("shortName") ?: default?.shortName ?: badRequest("missing shortName"),
                startDate = json.getString("startDate")?.let { LocalDate.parse(it) } ?: default?.startDate ?: badRequest("missing startDate"),
                endDate = json.getString("endDate")?.let { LocalDate.parse(it) } ?: default?.endDate ?: badRequest("missing endDate"),
                director = json.getString("director") ?: default?.director ?: "",
                country = (json.getString("country") ?: default?.country ?: "fr").let { if (it.isEmpty()) "fr" else it },
                location = json.getString("location") ?: default?.location ?: badRequest("missing location"),
                online = json.getBoolean("online") ?: default?.online ?: false,
                komi = json.getDouble("komi") ?: default?.komi ?: 7.5,
                rules = json.getString("rules")?.let { Rules.valueOf(it) } ?: default?.rules ?: Rules.FRENCH,
                gobanSize = json.getInt("gobanSize") ?: default?.gobanSize ?: 19,
                timeSystem = json.getObject("timeSystem")?.let { TimeSystem.fromJson(it) } ?: default?.timeSystem ?: badRequest("missing timeSystem"),
                rounds = json.getInt("rounds") ?: default?.rounds ?: badRequest("missing rounds"),
                pairing = json.getObject("pairing")?.let { Pairing.fromJson(it, default?.pairing) } ?: default?.pairing ?: badRequest("missing pairing")
            )
        else
            TeamTournament(
                id = json.getInt("id") ?: default?.id ?: nextTournamentId,
                type = type,
                name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
                shortName = json.getString("shortName") ?: default?.shortName ?: badRequest("missing shortName"),
                startDate = json.getString("startDate")?.let { LocalDate.parse(it) } ?: default?.startDate ?: badRequest("missing startDate"),
                endDate = json.getString("endDate")?.let { LocalDate.parse(it) } ?: default?.endDate ?: badRequest("missing endDate"),
                director = json.getString("director") ?: default?.director ?: "",
                country = json.getString("country") ?: default?.country ?: badRequest("missing country"),
                location = json.getString("location") ?: default?.location ?: badRequest("missing location"),
                online = json.getBoolean("online") ?: default?.online ?: false,
                komi = json.getDouble("komi") ?: default?.komi ?: 7.5,
                rules = json.getString("rules")?.let { Rules.valueOf(it) } ?: default?.rules ?: if (json.getString("country")?.lowercase(Locale.ROOT) ==  "fr") Rules.FRENCH else Rules.AGA,
                gobanSize = json.getInt("gobanSize") ?: default?.gobanSize ?: 19,
                timeSystem = json.getObject("timeSystem")?.let { TimeSystem.fromJson(it) } ?: default?.timeSystem ?: badRequest("missing timeSystem"),
                rounds = json.getInt("rounds") ?: default?.rounds ?: badRequest("missing rounds"),
                pairing = json.getObject("pairing")?.let { Pairing.fromJson(it, default?.pairing) } ?: default?.pairing ?: badRequest("missing pairing")
            )
    json.getArray("players")?.forEach { obj ->
        val pairable = obj as Json.Object
        tournament.players[pairable.getID("id")!!] = Player.fromJson(pairable)
    }
    if (tournament is TeamTournament) {
        json.getArray("teams")?.forEach { obj ->
            val team = obj as Json.Object
            tournament.teams[team.getID("id")!!] = tournament.teamFromJson(team)
        }
    }
    (json["games"] as Json.Array?)?.forEachIndexed { i, arr ->
        val round = i + 1
        val tournamentGames = tournament.games(round)
        val games = arr as Json.Array
        games.forEach { obj ->
            val game = obj as Json.Object
            tournamentGames[game.getID("id")!!] = Game.fromJson(game)
        }
    }
    return tournament
}

fun Tournament<*>.toJson() = Json.MutableObject(
    "id" to id,
    "type" to type.name,
    "name" to name,
    "shortName" to shortName,
    "startDate" to startDate.toString(),
    "endDate" to endDate.toString(),
    "director" to director,
    "country" to country,
    "location" to location,
    "online" to online,
    "komi" to komi,
    "rules" to rules.name,
    "gobanSize" to gobanSize,
    "timeSystem" to timeSystem.toJson(),
    "rounds" to rounds,
    "pairing" to pairing.toJson()
)

fun Tournament<*>.toFullJson(): Json.Object {
    val json = toJson()
    json["players"] = Json.Array(players.values.map { it.toJson() })
    if (this is TeamTournament) {
        json["teams"] = Json.Array(teams.values.map { it.toJson() })
    }
    json["games"] = Json.Array((1..lastRound()).mapTo(Json.MutableArray()) { round -> games(round).values.mapTo(Json.MutableArray()) { it.toJson() } });
    return json
}
