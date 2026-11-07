package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.displayRank
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A tournament loaded into the API, with the operations the OpenGotha-based tests share: read the
 * file's own pairings, pair a round, walk the alternative optimal pairings, force a pairing back in
 * and replay the file's results.
 *
 * Players are named the way both engines' cost dumps name them — "name firstname" — so a pairing can
 * be compared with an OpenGotha dump without any further mapping.
 */
class OpenGothaFixture(val id: Int) {

    val tournament: Json.Object = TestAPI.get("/api/tour/$id").asObject()
    val rounds = tournament.getInt("rounds")!!
    val shortName = tournament.getString("shortName") ?: ""
    val players: Json.Array = TestAPI.get("/api/tour/$id/part").asArray()

    val playerName: Map<ID, String> = players.map { it as Json.Object }
        .associate { it.getInt("id")!! to "${it.getString("name")} ${it.getString("firstname")}" }
    val playerId: Map<String, ID> = playerName.entries.associate { (id, name) -> name to id }

    companion object {
        const val BYE: ID = 0

        fun fromOpenGotha(file: File): OpenGothaFixture {
            val resource = file.readText(StandardCharsets.UTF_8)
            return OpenGothaFixture(TestAPI.post("/api/tour", resource).asObject().getInt("id")!!)
        }

        fun fromJson(tournament: Json.Object) =
            OpenGothaFixture(TestAPI.post("/api/tour", tournament).asObject().getInt("id")!!)

        /** Do two pairings pair the same players together? Colours are a separate question. */
        fun samePairs(some: Collection<Json.Object>, others: Collection<Json.Object>) =
            some.map { pairOf(it) }.toSet() == others.map { pairOf(it) }.toSet()

        fun pairOf(game: Json.Object) = setOf(game.getInt("w")!!, game.getInt("b")!!)
    }

    fun games(round: Int): List<Json.Object> =
        TestAPI.get("/api/tour/$id/res/$round").asArray().map { it as Json.Object }

    fun pairs(round: Int): Set<Set<ID>> = games(round).map { pairOf(it) }.toSet()

    /** Who these games have playing — the bye player included, everyone else left out. */
    fun field(games: Collection<Json.Object>): Set<ID> =
        games.flatMap { pairOf(it) }.filter { it != BYE }.toSet()

    fun named(pairs: Collection<Set<ID>>) = pairs.map { pair ->
        pair.sortedBy { it }.joinToString(" / ") { playerName[it] ?: "bye" }
    }.sorted()

    fun names(pair: Set<ID>) = pair.filter { it != BYE }.map { playerName[it]!! }.toSet()

    fun unpairAll() {
        for (round in rounds downTo 1) TestAPI.delete("/api/tour/$id/pair/$round", Json.Array("all"))
    }

    /**
     * Pairs [pairables] (all pairable players when null) for the round. A single pairable gets a bye
     * game, which is how a pairing is forced back in pair by pair.
     */
    fun pair(round: Int, pairables: Collection<ID>? = null, weights: File? = null, legacy: Boolean = false): List<Json.Object> {
        val payload =
            if (pairables == null) Json.Array("all")
            else Json.MutableArray().also { array -> pairables.forEach { array.add(it) } }
        val query = listOfNotNull(
            "legacy=$legacy".takeIf { legacy },
            weights?.let { "weights_output=$it" }
        ).joinToString("&", prefix = if (weights != null || legacy) "?" else "")
        return TestAPI.post("/api/tour/$id/pair/$round$query", payload).asArray().map { it as Json.Object }
    }

    /** Replaces the round's pairing with the next optimal one; false when there is no other. */
    fun nextAlternative(round: Int): Boolean =
        !TestAPI.post<Json>("/api/tour/$id/pair/$round?nav=next", Json.Object()).isObject

    /**
     * Forces a pairing in, pair by pair: the solver has no say when handed two players, so this
     * reproduces any pairing, however it was made. Colours and handicaps are the solver's until
     * [replay] puts the file's back.
     */
    fun force(round: Int, pairs: Collection<Set<ID>>) {
        TestAPI.delete("/api/tour/$id/pair/$round", Json.Array("all"))
        pairs.forEach { players -> pair(round, players.filter { it != BYE }) }
    }

    /** Puts the file's colours, handicaps and results back on the round's games. */
    fun replay(round: Int, reference: Collection<Json.Object>) {
        val byPair = games(round).associateBy { pairOf(it) }
        reference.forEach { source ->
            val game = byPair[pairOf(source)] ?: throw Error("round $round: ${named(listOf(pairOf(source)))} is not paired")
            if (game.getInt("w") != source.getInt("w") || game.getInt("h") != source.getInt("h")) {
                TestAPI.put("/api/tour/$id/pair/$round", Json.Object(
                    "id" to game.getInt("id"), "w" to source.getInt("w"), "b" to source.getInt("b"),
                    "h" to source.getInt("h").toString()
                ))
            }
            source.getString("r")?.let {
                TestAPI.put("/api/tour/$id/res/$round", Json.Object("id" to game.getInt("id"), "result" to it))
            }
        }
    }

    fun formatPlayer(player: Json.Object) =
        "${player.getString("name")} ${player.getString("firstname")} ${displayRank(player.getInt("rank")!!)}"

    fun formatGame(game: Json.Object) =
        "${game.getInt("t")}. white ${playerName[game.getInt("w")] ?: "BIP"} vs. black ${playerName[game.getInt("b")] ?: "BIP"} h${game.getInt("h") ?: 0}"

    fun formatGames(games: Collection<Json.Object>) = games.joinToString("\n") { formatGame(it) }
}
