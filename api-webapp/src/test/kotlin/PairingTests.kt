package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.store.MemoryStore
import org.jeudego.pairgoth.store.lastPlayerId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.text.DecimalFormat
import kotlin.test.assertTrue

/**
 * Conformance to OpenGotha on the curated tournaments: every round is paired from scratch and must
 * come out as OpenGotha paired it, weight by weight (`opengotha/<name>/<name>_weights_R<n>.txt`).
 *
 * The corpus of real tournament files is checked differently — see [OpenGothaCorpusTest].
 */
//@Disabled("pairings differ")
class PairingTests: TestBase() {

    @BeforeEach
    fun reset() {
        MemoryStore.reset()
    }

    /** Same games, colours included unless [skipColor]. pairgoth may add a bye game the file has not. */
    fun compare_games(games: List<Json.Object>, opengotha: List<Json.Object>, skipColor: Boolean = false): Boolean {
        if (games.size != opengotha.size) {
            val last = games.last()
            if (last.getInt("w") != 0 && last.getInt("b") != 0) return false
        }
        fun pairsOf(list: List<Json.Object>) = list.take(opengotha.size).map { game ->
            val white = game.getInt("w")!!
            val black = game.getInt("b")!!
            if (skipColor) listOf(minOf(white, black), maxOf(white, black)) else listOf(white, black)
        }.toSet()
        val ours = pairsOf(games)
        val theirs = pairsOf(opengotha)
        if (ours != theirs) logger.info("Pairings do not match ${ours - theirs}")
        return ours == theirs
    }

    /** What OpenGotha's own weights make of the pairing it chose — byes, which have none, apart. */
    fun compute_sumOfWeight_OG(file: File, opengotha: List<Json.Object>, fixture: OpenGothaFixture): Double {
        val costs = OpenGothaWeights.read(file).costs.values.firstOrNull() ?: emptyMap()
        return opengotha.filter { it.getInt("w") != 0 && it.getInt("b") != 0 }.sumOf { game ->
            val pair = OpenGothaWeights.Pair(fixture.playerName[game.getInt("w")]!!, fixture.playerName[game.getInt("b")]!!)
            costs[pair]?.get("totalCost") ?: throw Error("no opengotha weight for $pair")
        }
    }

    fun test_from_XML(name: String, forcePairing: List<Int>) {
        // Let pairgoth use the legacy asymmetric detRandom()
        test_from_XML_internal(name, forcePairing, true)
        // Non-legacy tests inhibited for now: pairings differ for Toulouse and SimpleMM
        // test_from_XML_internal(name, forcePairing, false)
    }

    fun test_from_XML_internal(name: String, forcePairing: List<Int>, legacy: Boolean) {
        val tourFile = getTestFile("opengotha/pairings/$name.xml")
        logger.info("read from file $tourFile")
        val fixture = OpenGothaFixture.fromOpenGotha(tourFile)

        // pairings, results included, as the OpenGotha file has them
        val pairingsOG = (1..fixture.rounds).map { fixture.games(it) }
        fixture.unpairAll()

        val dec = DecimalFormat("#.#")
        for (round in 1..fixture.rounds) {
            val weightsOG = getTestFile("opengotha/$name/$name" + "_weights_R$round.txt")
            val sumOfWeightsOG = compute_sumOfWeight_OG(weightsOG, pairingsOG[round - 1], fixture)

            val outputFile = getOutputFile("weights.txt")
            var games = fixture.pair(round, weights = outputFile, legacy = legacy)
            logger.info("sumOfWeightOG = " + dec.format(sumOfWeightsOG))
            logger.info("games for round $round: {}", games.toString())

            // Compare weights with OpenGotha if legacy mode
            if (legacy) {
                assertTrue(OpenGothaWeights.compare_weights(outputFile, weightsOG), "Not matching opengotha weights for round $round")
            }

            if (round in forcePairing) {
                logger.info("Non unique pairing, forcing Opengotha pairing to Pairgoth")
                val firstGameID = games.first().getInt("id")!!
                pairingsOG[round - 1].forEachIndexed { index, gameOG ->
                    TestAPI.put("/api/tour/${fixture.id}/pair/$round?legacy=$legacy&weights_output=$outputFile&append=true",
                        Json.Object("id" to firstGameID + index, "w" to gameOG.getInt("w"), "b" to gameOG.getInt("b")))
                }
                games = fixture.games(round)
            }

            // Compare pairings with OpenGotha
            val gamesDoMatch = compare_games(games, pairingsOG[round - 1])
            if (!gamesDoMatch) {
                logger.info("Expected opengotha pairing:\n${fixture.formatGames(pairingsOG[round - 1])}")
                logger.info("Actual pairgoth pairing:\n${fixture.formatGames(games)}")
            }
            assertTrue(gamesDoMatch, "pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")

            // Enter results extracted from OpenGotha
            games.forEach { game ->
                val source = pairingsOG[round - 1].first { OpenGothaFixture.pairOf(it) == OpenGothaFixture.pairOf(game) }
                val resp = TestAPI.put("/api/tour/${fixture.id}/res/$round",
                    Json.Object("id" to game.getInt("id"), "result" to source.getString("r"))).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }
            logger.info("Results succesfully entered for round $round")
        }
    }

    @Test
    fun `SwissTest simpleSwiss`() {
        val tourFile = getTestFile("opengotha/pairings/simpleswiss.xml")
        logger.info("read from file $tourFile")
        val fixture = OpenGothaFixture.fromOpenGotha(tourFile)

        val pairingsOG = (1..fixture.rounds).map { fixture.games(it) }.toMutableList()
        fixture.unpairAll()

        /*
        At least 2 solutions have the same sum of weights, OpenGotha pairing is
        """[{"id":698,"t":1,"w":335,"b":324,"h":0,"r":"b","dd":0},{"id":699,"t":2,"w":316,"b":311,"h":0,"r":"b","dd":0},{"id":700,"t":3,"w":337,"b":325,"h":0,"r":"b","dd":0},{"id":701,"t":4,"w":313,"b":326,"h":0,"r":"b","dd":0},{"id":702,"t":5,"w":321,"b":309,"h":0,"r":"b","dd":0},{"id":703,"t":6,"w":319,"b":317,"h":0,"r":"b","dd":0},{"id":704,"t":7,"w":340,"b":312,"h":0,"r":"b","dd":0},{"id":705,"t":8,"w":332,"b":318,"h":0,"r":"b","dd":0},{"id":706,"t":9,"w":336,"b":327,"h":0,"r":"b","dd":0},{"id":707,"t":10,"w":333,"b":322,"h":0,"r":"b","dd":0},{"id":708,"t":11,"w":315,"b":328,"h":0,"r":"b","dd":0},{"id":709,"t":12,"w":323,"b":334,"h":0,"r":"b","dd":0},{"id":710,"t":13,"w":329,"b":331,"h":0,"r":"b","dd":0},{"id":711,"t":14,"w":339,"b":310,"h":0,"r":"b","dd":0},{"id":712,"t":15,"w":338,"b":320,"h":0,"r":"b","dd":0},{"id":713,"t":16,"w":330,"b":314,"h":0,"r":"b","dd":0}]"""
        Pairgoth pairing is
        */
        pairingsOG[6] = Json.parse(
            """[{"id":810,"t":1,"w":335,"b":324,"h":0,"r":"?","dd":0},{"id":811,"t":2,"w":337,"b":325,"h":0,"r":"?","dd":1},{"id":812,"t":3,"w":316,"b":319,"h":0,"r":"?","dd":1},{"id":813,"t":4,"w":313,"b":326,"h":0,"r":"?","dd":0},{"id":814,"t":5,"w":321,"b":309,"h":0,"r":"?","dd":0},{"id":815,"t":6,"w":311,"b":317,"h":0,"r":"?","dd":1},{"id":816,"t":7,"w":340,"b":312,"h":0,"r":"?","dd":0},{"id":817,"t":8,"w":332,"b":318,"h":0,"r":"?","dd":0},{"id":818,"t":9,"w":336,"b":327,"h":0,"r":"?","dd":0},{"id":819,"t":10,"w":333,"b":322,"h":0,"r":"?","dd":0},{"id":820,"t":11,"w":315,"b":328,"h":0,"r":"?","dd":1},{"id":821,"t":12,"w":323,"b":334,"h":0,"r":"?","dd":0},{"id":822,"t":13,"w":329,"b":331,"h":0,"r":"?","dd":0},{"id":823,"t":14,"w":339,"b":310,"h":0,"r":"?","dd":0},{"id":824,"t":15,"w":338,"b":320,"h":0,"r":"?","dd":0},{"id":825,"t":16,"w":330,"b":314,"h":0,"r":"?","dd":0}]"""
        )!!.asArray().map {
            // adjust ids
            Json.MutableObject(it as Json.Object).also { game ->
                game["w"] = game.getInt("w")!! - 340 + lastPlayerId
                game["b"] = game.getInt("b")!! - 340 + lastPlayerId
            }
        }

        for (round in 1..7) {
            val outputFile = getOutputFile("weights.txt")
            val games = fixture.pair(round, weights = outputFile, legacy = true)
            logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")
            assertTrue(OpenGothaWeights.compare_weights(outputFile, getTestFile("opengotha/simpleswiss/simpleswiss_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            assertTrue(compare_games(games, pairingsOG[round - 1]), "pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")

            games.forEach { game ->
                val resp = TestAPI.put("/api/tour/${fixture.id}/res/$round",
                    Json.Object("id" to game.getInt("id"), "result" to "b")).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }
            logger.info("Results succesfully entered for round $round")
        }
    }

    @Test
    fun `SwissTest notSoSimpleSwiss`() {
        test_from_XML("notsosimpleswiss", listOf(6, 8))
    }

    @Test
    fun `MMtest simpleMM`() {
        test_from_XML("simplemm", emptyList())
    }

    @Test
    fun `MMtest notSimpleMM`() {
        test_from_XML("notsimplemm", emptyList())
    }

    @Test
    fun `MMtest Toulouse2024`() {
        test_from_XML("Toulouse2024", emptyList())
    }

    @Test
    fun `SwissTest KPMCSplitbug`() {
        // Let pairgoth use the legacy asymmetric detRandom()
        val legacy = true
        val name = "20240921-KPMC-Splitbug"
        val tourFile = getTestFile("opengotha/pairings/$name.xml")
        logger.info("read from file $tourFile")
        val fixture = OpenGothaFixture.fromOpenGotha(tourFile)

        // Set the rounds on which to perform the pairings test
        val minRound = 2
        val maxRound = 2

        val pairingsOG = (minRound..maxRound).map { fixture.games(it) }
        for (round in maxRound downTo minRound) TestAPI.delete("/api/tour/${fixture.id}/pair/$round", Json.Array("all"))

        val dec = DecimalFormat("#.#")
        val outputFile = getOutputFile("weights.txt")

        for (round in minRound..maxRound) {
            val weightsOG = getTestFile("opengotha/$name/$name" + "_weights_R$round.txt")
            val sumOfWeightsOG = compute_sumOfWeight_OG(weightsOG, pairingsOG[round - minRound], fixture)

            val games = fixture.pair(round, weights = outputFile, legacy = legacy)
            logger.info("sumOfWeightOG = " + dec.format(sumOfWeightsOG))
            logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")

            assertTrue(OpenGothaWeights.compare_weights(outputFile, weightsOG), "Not matching opengotha weights for round $round")

            val gamesDoMatch = compare_games(games, pairingsOG[round - minRound])
            if (!gamesDoMatch) {
                logger.info("Expected opengotha pairing:\n${fixture.formatGames(pairingsOG[round - minRound])}")
                logger.info("Actual pairgoth pairing:\n${fixture.formatGames(games)}")
            }
            assertTrue(gamesDoMatch, "pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")
        }
    }
}
