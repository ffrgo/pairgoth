package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.store.MemoryStore
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertTrue

/**
 * Replays a corpus of real OpenGotha tournament files, round by round, and checks that pairgoth
 * pairs like OpenGotha did.
 *
 * Each round is paired on the field the file records for it — the players it actually paired, plus
 * its bye player — and compared with the pairing the instrumented OpenGotha 3.52 produces from the
 * same state (`opengotha/corpus/<name>.og.txt.gz`, see the `instrumented-3.52` branch of the
 * opengotha repository). A pairing that differs is not a failure yet: pairgoth walks its remaining
 * optimal pairings, and only a pairing OpenGotha's is *not* among fails the round. Then, matched or
 * not, the file's own pairing and results are forced back in, so the next round starts from the
 * history that was actually played.
 *
 * Corpus directory: `src/test/resources/opengotha/corpus`, or -Dopengotha.corpus=<dir>.
 */
class OpenGothaCorpusTest: TestBase() {

    /** How many alternative optimal pairings to walk before giving up on a round. */
    private val maxAlternatives = 200

    @TestFactory
    fun corpus(): List<DynamicTest> {
        val dir = System.getProperty("opengotha.corpus")?.let { File(it) } ?: getTestFile("opengotha/corpus")
        val files = dir.listFiles { file: File -> file.name.endsWith(".xml") }?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) logger.warn("no opengotha file in $dir")
        return files.map { file -> DynamicTest.dynamicTest(file.nameWithoutExtension) { check(file) } }
    }

    private fun check(file: File) {
        MemoryStore.reset()
        val reference = File(file.parentFile, "${file.nameWithoutExtension}.og.txt.gz")
        if (!reference.exists()) abort<Unit>("no opengotha reference for ${file.name}: generate ${reference.name}")
        val version = Regex("fullVersionNumber=\"([\\d.]+)\"").find(file.readText())?.groupValues?.get(1)
        if (version?.startsWith("3.52") != true) {
            // the pairing algorithm changes between versions: comparing with the wrong one proves nothing
            abort<Unit>("${file.name} was saved by opengotha $version, and only 3.52 is instrumented")
        }

        val opengotha = OpenGothaWeights.read(reference)
        val fixture = OpenGothaFixture.fromOpenGotha(file)
        val played = (1..fixture.rounds).associateWith { fixture.games(it) }.filterValues { it.isNotEmpty() }
        logger.info("${fixture.shortName}: ${fixture.players.size} players, ${played.size} played rounds")
        fixture.unpairAll()

        val failures = mutableListOf<String>()
        for (round in played.keys.sorted()) {
            val adjusted = adjustments(fixture.shortName, round)
            val expected = opengotha.pairings[round] ?: throw Error("round $round missing from ${reference.name}")

            // pair the round on the file's field, minus what the operator paired by hand, minus the
            // bye player when the operator chose one (pairgoth would pick its own)
            val handmade = adjusted.handmade.map { pair -> pair.map { fixture.playerId[it] ?: throw Error("unknown player $it") }.toSet() }
            val forcedBye = adjusted.bye?.let { fixture.playerId[it] ?: throw Error("unknown player $it") }
            val field = fixture.field(played[round]!!) - handmade.flatten().toSet() - setOfNotNull(forcedBye)
            val weights = getOutputFile("${fixture.shortName}-weights-R$round.txt")
            // legacy: opengotha's asymmetric detRandom, which the seeding criterion draws on
            fixture.pair(round, field, weights = weights, legacy = true)
            if (forcedBye != null) fixture.pair(round, listOf(forcedBye), legacy = true)

            val comparison = OpenGothaWeights.compare(round, OpenGothaWeights.read(weights), opengotha)
            if (comparison.differing > 0) logger.info("round $round: weights, $comparison")

            var alternatives = 0
            while (asNames(fixture, round) != asNames(expected) && alternatives < maxAlternatives) {
                if (!fixture.nextAlternative(round)) break
                alternatives++
            }
            val matched = asNames(fixture, round) == asNames(expected)
            if (matched) logger.info("round $round: matches opengotha${if (alternatives > 0) " (optimal pairing #${alternatives + 1})" else ""}")
            else failures.add(report(fixture, round, expected, opengotha, weights, alternatives))

            reportFileDrift(fixture, round, played[round]!!, expected, adjusted, handmade)

            // continue on what was really played, whatever we would have paired
            fixture.force(round, played[round]!!.map { OpenGothaFixture.pairOf(it) })
            fixture.replay(round, played[round]!!)
        }
        assertTrue(failures.isEmpty(), "${fixture.shortName}: ${failures.size} round(s) differ from opengotha\n${failures.joinToString("\n")}")
    }

    /** Both sides as sets of player names, a bye being a pair of one. */
    private fun asNames(fixture: OpenGothaFixture, round: Int) =
        fixture.pairs(round).map { fixture.names(it) }.toSet()

    private fun asNames(pairing: OpenGothaWeights.Pairing) =
        pairing.pairs + setOfNotNull(pairing.bye?.let { setOf(it) })

    /** What differs, and what the two engines' weights say about it — nothing else. */
    private fun report(
        fixture: OpenGothaFixture, round: Int, expected: OpenGothaWeights.Pairing,
        opengotha: OpenGothaWeights.Dump, weights: File, alternatives: Int
    ): String {
        val ours = asNames(fixture, round)
        val theirs = asNames(expected)
        val mine = OpenGothaWeights.read(weights)
        val lines = mutableListOf(
            "round $round: opengotha's pairing is not among pairgoth's ${alternatives + 1} optimal pairing(s)"
        )
        (ours - theirs).forEach { lines.add("    pairgoth  only: ${it.joinToString(" / ")}") }
        (theirs - ours).forEach { lines.add("    opengotha only: ${it.joinToString(" / ")}") }
        ((ours - theirs) + (theirs - ours)).filter { it.size == 2 }.forEach { pair ->
            val key = OpenGothaWeights.Pair(pair)
            val differences = OpenGothaWeights.differences(mine.cost(round, key), opengotha.cost(round, key))
            if (differences.isNotEmpty()) {
                lines.add("    weights $key (pairgoth - opengotha): " +
                    differences.entries.joinToString(", ") { (criterion, delta) -> "$criterion %+.0f".format(delta) })
            }
        }
        val ourTotal = opengotha.total(round, ours)
        val theirTotal = opengotha.total(round, theirs)
        lines.add("    total under opengotha's weights: pairgoth %.0f, opengotha %.0f (%+.0f)".format(ourTotal, theirTotal, ourTotal - theirTotal))
        return lines.joinToString("\n")
    }

    /**
     * Whether the file's own pairing is what OpenGotha would produce from the file's final state.
     * Often it is not — a hand repair, or a round paired before the previous one was over — which
     * says nothing about pairgoth, but should not pass unnoticed either.
     */
    private fun reportFileDrift(
        fixture: OpenGothaFixture, round: Int, played: List<Json.Object>,
        expected: OpenGothaWeights.Pairing, adjusted: Adjustments, handmade: List<Set<ID>>
    ) {
        val fromFile = played.map { fixture.names(OpenGothaFixture.pairOf(it)) }.toSet() - handmade.map { fixture.names(it) }.toSet()
        if (fromFile == asNames(expected) - handmade.map { fixture.names(it) }.toSet()) return
        val reason = adjusted.fileUnreproducible
        if (reason == null) logger.warn("round $round: the file's own pairing is not what opengotha replays, and nothing says why")
        else logger.info("round $round: the file's own pairing differs from opengotha's — $reason")
    }

    /**
     * What an operator did by hand, per tournament and round: the algorithm cannot reproduce it, so
     * the test replays it. Whatever is declared here must also have been passed to the reference
     * generator, or the two sides pair different fields — the flags are named after these.
     */
    private fun adjustments(code: String, round: Int) = when {

        // opengotha/corpus/open-2026.og.txt.gz was generated with:
        //   ./instrumented-3.52/build.sh open-2026.xml open-2026.og.txt \
        //       '--bye=1:Roux Guillaume' '--handmade=1:Frangi Manuel|Rabasse Maël' '--bye=5:Roux Guillaume'
        code == "open-2026" && round == 1 -> Adjustments(
            // Erik Forel did not show up, and Sylvain Bousquet should not have been in the field
            // (he only entered at round 7). That left their two opponents, Frangi and Rabasse,
            // without a game, and the operator paired them together — after the round had started.
            handmade = listOf(setOf("Frangi Manuel", "Rabasse Maël")),
            bye = "Roux Guillaume"
        )
        code == "open-2026" && round == 5 -> Adjustments(bye = "Roux Guillaume")
        code == "open-2026" && round in listOf(2, 3, 6) -> Adjustments(
            // paired while a game of the previous round was still running: the scores that pairing
            // saw are lost, so the file's pairing crosses score groups where a replay's does not
            fileUnreproducible = "paired before the previous round was over"
        )

        else -> Adjustments()
    }

    private class Adjustments(
        /** Pairs the operator made himself: out of the field, and out of the comparison. */
        val handmade: List<Set<String>> = emptyList(),
        /** The bye player the operator chose, when it is not the one an engine would pick. */
        val bye: String? = null,
        /** Why the file's own pairing cannot be replayed, when that is known and expected. */
        val fileUnreproducible: String? = null
    )
}
