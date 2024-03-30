package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.ajbrown.namemachine.NameGenerator
import org.ajbrown.namemachine.NameGeneratorOptions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random

class LoadTest: TestBase() {

    companion object {
        val PLAYERS = 1500
        val ROUNDS = 10
        val SKIP_RATIO = 0.1 // 10%

        @JvmStatic
        fun main(args: Array<String>) {
            System.out.println("Press Enter to continue...")
            try {
                System.`in`.read()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            LoadTest().testVeryBigTournament()
        }

        private val nameGenerator = NameGenerator(
            NameGeneratorOptions().apply {
                randomSeed = 123456L
            }
        )
        private val rand = Random(123456L)
        private fun generateRating() = rand.nextInt(-900, 2800)
        private val countries = listOf("at", "be", "ca", "ch", "cz", "de", "dk", "dz", "ee", "es", "fi", "fr", "ga", "gb", "gr", "hr", "hu", "ie", "il", "in", "it", "jp", "ke", "kh", "kr", "lt", "ma", "ml", "nl", "no", "pl", "pt", "ro", "rs", "ru", "rw", "se", "si", "sk", "sn", "tn", "tr", "tw", "ua", "ug", "us", "vn", "za", "zm")
        fun generatePlayer(): Json.Object {
            val name = nameGenerator.generateName()
            val rating = generateRating()
            return Json.Object(
                "name" to name.lastName,
                "firstname" to name.firstName,
                "country" to countries.random(rand),
                "club" to "cl${rand.nextInt(100)}",
                "rating" to rating,
                "rank" to (rating - 2050)/100,
                "final" to true,
                "skip" to (0..ROUNDS - 1).map {
                    rand.nextDouble() < SKIP_RATIO
                }.mapIndexedNotNullTo(Json.MutableArray()) { index, skip ->
                    if (skip) (index + 1) else null
                }
            )
        }
    }

    @Disabled("meant to be run manually")
    @Test
    fun testVeryBigTournament() {
        val before = System.currentTimeMillis()
        try {
            val tour = TestAPI.post("/api/tour", Json.Object(
                "type" to "INDIVIDUAL",
                "name" to "Mon Tournoi",
                "shortName" to "mon-tournoi",
                "startDate" to "2023-05-10",
                "endDate" to "2023-05-12",
                "country" to "FR",
                "location" to "Marseille",
                "online" to false,
                "timeSystem" to Json.Object(
                    "type" to "FISCHER",
                    "mainTime" to 1200,
                    "increment" to 10
                ),
                "rounds" to ROUNDS,
                "pairing" to Json.Object(
                    "type" to "MAC_MAHON",
                    "handicap" to Json.Object(
                        "correction" to 1
                    )
                )
            )).asObject().getInt("id")!!
            repeat(PLAYERS) {
                TestAPI.post("/api/tour/$tour/part", generatePlayer())
            }
            getOutputFile("verybig-nopairing.tour").printWriter().use {
                it.println(TestAPI.getJson("/api/tour/$tour"))
            }
            repeat(ROUNDS) {
                val round = it + 1
                val resp = TestAPI.post("/api/tour/$tour/pair/$round", Json.Array("all"))
                if (!resp.isArray) {
                    throw Error(resp.toString())
                }
                val games = resp.asArray()
                games.map { (it as Json.Object).getInt("id")!! }.forEach { gameId ->
                    TestAPI.put("/api/tour/$tour/res/$round", Json.Object(
                        "id" to gameId,
                        // TODO - credible probabilistic results based on scores
                        "result" to if (rand.nextBoolean()) "w" else "b"
                    ))
                }
            }
            // val standings = TestAPI.get("/api/tour/$tour/standings/$ROUNDS")
            // logger.info(standings.toString())
            getOutputFile("verybig.tour").printWriter().use {
                it.println(TestAPI.getJson("/api/tour/$tour"))
            }
        } finally {
            val after = System.currentTimeMillis()
            logger.info("testVeryBigTournament ran in ${(after - before) / 1000} seconds")
        }
    }
}
