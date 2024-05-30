package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.pairing.solver.BaseSolver
import org.jeudego.pairgoth.store.MemoryStore
import org.jeudego.pairgoth.store.lastPlayerId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

//@Disabled("pairings differ")
class PairingTests: TestBase() {

    @BeforeEach
    fun reset() {
        MemoryStore.reset()
    }

    fun compare_weights(file1: File, file2: File, skipSeeding: Boolean = false):Boolean {
        BaseSolver.weightsLogger!!.flush()
        // Maps to store name pairs and costs
        val map1 = HashMap<Pair<String, String>, List<Double>>()
        val map2 = HashMap<Pair<String, String>, List<Double>>()
        var count: Int = 1

        for (file in listOf(file1, file2)) {

            // Read lines
            val lines = file.readLines()

            // Store headers
            val header1 = lines[0]
            val header2 = lines[1]

            logger.info("Reading weights file "+file)

            // Loop through sections
            for (i in 2..lines.size-1 step 12) {
                // Get name pair
                val name1 = lines[i].split("=")[1]
                val name2 = lines[i+1].split("=")[1]

                // Nested loop over costs
                val costs = mutableListOf<Double>()
                for (j in i + 2..i + 11) {
                    val parts = lines[j].split("=")
                    costs.add(parts[1].toDouble())
                }

                val tmp_pair = if (name1 > name2) Pair(name1,name2) else Pair(name2,name1)
                // Add to map
                if (count == 1) {
                    map1[tmp_pair] = costs
                } else {
                    map2[tmp_pair] = costs
                }
            }
            count += 1

        }

        var identical = true
        for ((key, value) in map1) {
            // Check if key exists in both
            if (map2.containsKey(key)) {
                // Compare values
                //logger.info("Comparing $key")
                val isValid = if (!skipSeeding) {
                    abs(value!![9] - map2[key]!![9])>10 && identical==true
                } else {
                    abs((value!![9]-value!![6]-value!![5]) - (map2[key]!![9]-map2[key]!![6]-map2[key]!![5]))>10 && identical==true
                }
                if (isValid) {
                    // Key exists but values differ - print key
                    logger.info("Difference found at $key")
                    logger.info("baseDuplicateGameCost =   "+value!![0].toString()+"   "+map2[key]!![0].toString())
                    logger.info("baseRandomCost        =   "+value!![1].toString()+"   "+map2[key]!![1].toString())
                    logger.info("baseBWBalanceCost     =   "+value!![2].toString()+"   "+map2[key]!![2].toString())
                    logger.info("mainCategoryCost      =   "+value!![3].toString()+"   "+map2[key]!![3].toString())
                    logger.info("mainScoreDiffCost     =   "+value!![4].toString()+"   "+map2[key]!![4].toString())
                    logger.info("mainDUDDCost          =   "+value!![5].toString()+"   "+map2[key]!![5].toString())
                    logger.info("mainSeedCost          =   "+value!![6].toString()+"   "+map2[key]!![6].toString())
                    logger.info("secHandiCost          =   "+value!![7].toString()+"   "+map2[key]!![7].toString())
                    logger.info("secGeoCost            =   "+value!![8].toString()+"   "+map2[key]!![8].toString())
                    logger.info("totalCost             =   "+value!![9].toString()+"   "+map2[key]!![9].toString())
                    identical = false
                }
            }
        }
        return identical
    }

    fun compare_games(games:Json.Array, opengotha:Json.Array, skipColor: Boolean = false): Boolean{
        if (games.size != opengotha.size) {
            val tmp = Game.fromJson(games.getJson(games.size-1)!!.asObject())
            if ((tmp.white != 0) and (tmp.black != 0)) {return false}
        }
        val gamesPair = mutableSetOf<Pair<ID,ID>>()
        val openGothaPair = mutableSetOf<Pair<ID,ID>>()
        for (i in 0 until opengotha.size) {
            val tmp = Game.fromJson(games.getJson(i)!!.asObject().let {
                Json.MutableObject(it).set("t", 0) // hack to fill the table to make fromJson() happy
            })
            val tmpOG = Game.fromJson(opengotha.getJson(i)!!.asObject().let {
                Json.MutableObject(it).set("t", 0) // hack to fill the table to make fromJson() happy
            })
            if (skipColor) {
                gamesPair.add(Pair(min(tmp.white, tmp.black), max(tmp.white, tmp.black)))
                openGothaPair.add(Pair(min(tmpOG.white, tmpOG.black), max(tmpOG.white, tmpOG.black)))
            } else {
                gamesPair.add(Pair(tmp.white, tmp.black))
                openGothaPair.add(Pair(tmpOG.white, tmpOG.black))
            }
        }
        if (gamesPair!=openGothaPair) {
            logger.info("Pairings do not match "+gamesPair.asSequence().minus(openGothaPair).map {it}.toList().toString())
        }
        return gamesPair==openGothaPair
    }

    fun test_from_XML(name:String){
        // read tournament with pairing
        val file = getTestFile("opengotha/pairings/$name.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")

        // Get pairings (including results) from OpenGotha file
        val pairingsOG = mutableListOf<Json.Array>()
        for (round in 1..tournament.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$id/res/$round").asArray()
            pairingsOG.add(games)
        }

        // Delete pairings
        for (round in tournament.getInt("rounds")!! downTo 1) {
            TestAPI.delete("/api/tour/$id/pair/$round", Json.Array("all"))
        }

        var games: Json.Array
        var firstGameID: Int

        for (round in 1..tournament.getInt("rounds")!!) {
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))
            // Call Pairgoth pairing solver to generate games
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")

            // Compare weights with OpenGotha
            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/$name/$name"+"_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            // Compare pairings with OpenGotha
            assertTrue(compare_games(games, pairingsOG[round - 1]), "pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")

            // Enter results extracted from OpenGotha
            firstGameID = (games.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
            for (i in 0 until pairingsOG[round - 1].size) {
                val gameID = firstGameID + i
                // find corresponding game (matching white id)
                for (j in 0 until pairingsOG[round - 1].size) {
                    val gameOG = pairingsOG[round - 1].getJson(j)!!.asObject()// ["r"] as String?
                    if (gameOG["w"] == games.getJson(i)!!.asObject()["w"]) {
                        val gameRes = gameOG["r"] as String?
                        resp = TestAPI.put("/api/tour/$id/res/$round", Json.parse("""{"id":$gameID,"result":"$gameRes"}""")).asObject()
                        assertTrue(resp.getBoolean("success") == true, "expecting success")
                        break
                    }
                }
            }
            logger.info("Results succesfully entered for round $round")
        }
    }

    @Test
    fun `SwissTest simpleSwiss`() {
        // read tournament with pairing
        var file = getTestFile("opengotha/pairings/simpleswiss.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")

        val pairingsOG = mutableListOf<String>()
        for (round in 1..tournament.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$id/res/$round").asArray()
            logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")
            pairingsOG.add(games.toString())
        }

        for (round in tournament.getInt("rounds")!! downTo 1) {
            TestAPI.delete("/api/tour/$id/pair/$round", Json.Array("all"))
        }

        /*
        At least 2 solutions have the same sum of weights, OpenGotha pairing is
        """[{"id":698,"t":1,"w":335,"b":324,"h":0,"r":"b","dd":0},{"id":699,"t":2,"w":316,"b":311,"h":0,"r":"b","dd":0},{"id":700,"t":3,"w":337,"b":325,"h":0,"r":"b","dd":0},{"id":701,"t":4,"w":313,"b":326,"h":0,"r":"b","dd":0},{"id":702,"t":5,"w":321,"b":309,"h":0,"r":"b","dd":0},{"id":703,"t":6,"w":319,"b":317,"h":0,"r":"b","dd":0},{"id":704,"t":7,"w":340,"b":312,"h":0,"r":"b","dd":0},{"id":705,"t":8,"w":332,"b":318,"h":0,"r":"b","dd":0},{"id":706,"t":9,"w":336,"b":327,"h":0,"r":"b","dd":0},{"id":707,"t":10,"w":333,"b":322,"h":0,"r":"b","dd":0},{"id":708,"t":11,"w":315,"b":328,"h":0,"r":"b","dd":0},{"id":709,"t":12,"w":323,"b":334,"h":0,"r":"b","dd":0},{"id":710,"t":13,"w":329,"b":331,"h":0,"r":"b","dd":0},{"id":711,"t":14,"w":339,"b":310,"h":0,"r":"b","dd":0},{"id":712,"t":15,"w":338,"b":320,"h":0,"r":"b","dd":0},{"id":713,"t":16,"w":330,"b":314,"h":0,"r":"b","dd":0}]"""
        Pairgoth pairing is
        */
        pairingsOG[6] = Json.parse(
            """[{"id":810,"t":1,"w":335,"b":324,"h":0,"r":"?","dd":0},{"id":811,"t":2,"w":337,"b":325,"h":0,"r":"?","dd":1},{"id":812,"t":3,"w":316,"b":319,"h":0,"r":"?","dd":1},{"id":813,"t":4,"w":313,"b":326,"h":0,"r":"?","dd":0},{"id":814,"t":5,"w":321,"b":309,"h":0,"r":"?","dd":0},{"id":815,"t":6,"w":311,"b":317,"h":0,"r":"?","dd":1},{"id":816,"t":7,"w":340,"b":312,"h":0,"r":"?","dd":0},{"id":817,"t":8,"w":332,"b":318,"h":0,"r":"?","dd":0},{"id":818,"t":9,"w":336,"b":327,"h":0,"r":"?","dd":0},{"id":819,"t":10,"w":333,"b":322,"h":0,"r":"?","dd":0},{"id":820,"t":11,"w":315,"b":328,"h":0,"r":"?","dd":1},{"id":821,"t":12,"w":323,"b":334,"h":0,"r":"?","dd":0},{"id":822,"t":13,"w":329,"b":331,"h":0,"r":"?","dd":0},{"id":823,"t":14,"w":339,"b":310,"h":0,"r":"?","dd":0},{"id":824,"t":15,"w":338,"b":320,"h":0,"r":"?","dd":0},{"id":825,"t":16,"w":330,"b":314,"h":0,"r":"?","dd":0}]"""
        )!!.asArray().mapTo(Json.MutableArray()) {
            // adjust ids
            Json.MutableObject(it as Json.Object).also { game ->
                game["w"] = game.getInt("w")!! - 340 + lastPlayerId
                game["b"] = game.getInt("b")!! - 340 + lastPlayerId
            }
        }.toString()

        var games: Json.Array
        var firstGameID: Int

        for (round in 1..7) {
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info("games for round $round: {}", games.toString().slice(0..50) + "...")
            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/simpleswiss/simpleswiss_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            assertTrue(compare_games(games, Json.parse(pairingsOG[round - 1])!!.asArray()),"pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")

            firstGameID = (games.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
            for (gameID in firstGameID..firstGameID + 15) {
                resp = TestAPI.put("/api/tour/$id/res/$round", Json.parse("""{"id":$gameID,"result":"b"}""")).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }
            logger.info("Results succesfully entered for round $round")
        }
    }

    @Test
    fun `SwissTest notSoSimpleSwiss`() {
        test_from_XML("notsosimpleswiss")
    }

    @Test
    fun `MMtest simpleMM`() {
        test_from_XML("simplemm")
    }

    @Test
    fun `MMtest notSimpleMM`() {
        test_from_XML("notsimplemm")
    }

    @Test
    fun `MMtest Toulouse2024`() {
        test_from_XML("Toulouse2024")
    }
}