package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.pairing.solver.BaseSolver
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

//@Disabled("pairings differ")
class PairingTests: TestBase() {

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
                    abs((value!![9]-value!![6]) - (map2[key]!![9]-map2[key]!![6]))>10 && identical==true
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

        //return map1==map2
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
        return gamesPair==openGothaPair
    }

    fun compare_string(string1:String, string2:String): String{
        for (i in 0..string1.length) {
            // Check if key exists in both
            if (string1[i] != string2[i]) {
                return "at position "+i.toString()+" "+string1.substring(i-10,i+2)+" != "+string2.substring(i-10,i+2)
            }
        }
        return "strings are identical"
    }

    @Test
    fun `008 simple swiss tournament`() {

/*                // read tournament with pairing
                var fileOG = getTestFile("opengotha/pairings/simpleswiss_7R.xml")
                logger.info("read from file $fileOG")
                val resourceOG = fileOG.readText(StandardCharsets.UTF_8)
                val respOG = TestAPI.post("/api/tour", resourceOG)
                val idOG = respOG.asObject().getInt("id")
                val tournamentOG = TestAPI.get("/api/tour/$idOG").asObject()
                logger.info(tournamentOG.toString().slice(0..50) + "...")
                val playersOG = TestAPI.get("/api/tour/$idOG/part").asArray()
                //logger.info(players.toString().slice(0..50) + "...")
                //logger.info(playersOG.toString())

                val pairingsOG = mutableListOf<String>()
                for (round in 1..tournamentOG.getInt("rounds")!!) {
                    val games = TestAPI.get("/api/tour/$idOG/res/$round").asArray()
                    logger.info("games for round $round: {}", games.toString())
                    pairingsOG.add(games.toString())
                }*/

        val pairingsR1 = """[{"id":603,"t":1,"w":333,"b":338,"h":0,"r":"b","dd":0},{"id":604,"t":2,"w":324,"b":322,"h":0,"r":"b","dd":0},{"id":605,"t":3,"w":340,"b":332,"h":0,"r":"b","dd":0},{"id":606,"t":4,"w":321,"b":317,"h":0,"r":"b","dd":0},{"id":607,"t":5,"w":341,"b":316,"h":0,"r":"b","dd":0},{"id":608,"t":6,"w":312,"b":325,"h":0,"r":"b","dd":0},{"id":609,"t":7,"w":315,"b":314,"h":0,"r":"b","dd":0},{"id":610,"t":8,"w":331,"b":337,"h":0,"r":"b","dd":0},{"id":611,"t":9,"w":311,"b":326,"h":0,"r":"b","dd":0},{"id":612,"t":10,"w":320,"b":336,"h":0,"r":"b","dd":0},{"id":613,"t":11,"w":323,"b":318,"h":0,"r":"b","dd":0},{"id":614,"t":12,"w":310,"b":339,"h":0,"r":"b","dd":0},{"id":615,"t":13,"w":313,"b":327,"h":0,"r":"b","dd":0},{"id":616,"t":14,"w":330,"b":319,"h":0,"r":"b","dd":0},{"id":617,"t":15,"w":329,"b":334,"h":0,"r":"b","dd":0},{"id":618,"t":16,"w":335,"b":328,"h":0,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":619,"t":1,"w":334,"b":338,"h":0,"r":"b","dd":0},{"id":620,"t":2,"w":332,"b":322,"h":0,"r":"b","dd":0},{"id":621,"t":3,"w":317,"b":325,"h":0,"r":"b","dd":0},{"id":622,"t":4,"w":316,"b":326,"h":0,"r":"b","dd":0},{"id":623,"t":5,"w":318,"b":314,"h":0,"r":"b","dd":0},{"id":624,"t":6,"w":339,"b":337,"h":0,"r":"b","dd":0},{"id":625,"t":7,"w":319,"b":336,"h":0,"r":"b","dd":0},{"id":626,"t":8,"w":328,"b":327,"h":0,"r":"b","dd":0},{"id":627,"t":9,"w":340,"b":324,"h":0,"r":"b","dd":0},{"id":628,"t":10,"w":321,"b":312,"h":0,"r":"b","dd":0},{"id":629,"t":11,"w":311,"b":341,"h":0,"r":"b","dd":0},{"id":630,"t":12,"w":323,"b":315,"h":0,"r":"b","dd":0},{"id":631,"t":13,"w":331,"b":310,"h":0,"r":"b","dd":0},{"id":632,"t":14,"w":330,"b":320,"h":0,"r":"b","dd":0},{"id":633,"t":15,"w":335,"b":313,"h":0,"r":"b","dd":0},{"id":634,"t":16,"w":329,"b":333,"h":0,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":635,"t":1,"w":327,"b":338,"h":0,"r":"b","dd":0},{"id":636,"t":2,"w":322,"b":325,"h":0,"r":"b","dd":0},{"id":637,"t":3,"w":337,"b":314,"h":0,"r":"b","dd":0},{"id":638,"t":4,"w":326,"b":336,"h":0,"r":"b","dd":0},{"id":639,"t":5,"w":318,"b":317,"h":0,"r":"b","dd":0},{"id":640,"t":6,"w":313,"b":312,"h":0,"r":"b","dd":0},{"id":641,"t":7,"w":334,"b":319,"h":0,"r":"b","dd":0},{"id":642,"t":8,"w":333,"b":328,"h":0,"r":"b","dd":0},{"id":643,"t":9,"w":315,"b":316,"h":0,"r":"b","dd":0},{"id":644,"t":10,"w":339,"b":320,"h":0,"r":"b","dd":0},{"id":645,"t":11,"w":324,"b":310,"h":0,"r":"b","dd":0},{"id":646,"t":12,"w":332,"b":341,"h":0,"r":"b","dd":0},{"id":647,"t":13,"w":321,"b":340,"h":0,"r":"b","dd":0},{"id":648,"t":14,"w":329,"b":323,"h":0,"r":"b","dd":0},{"id":649,"t":15,"w":330,"b":311,"h":0,"r":"b","dd":0},{"id":650,"t":16,"w":335,"b":331,"h":0,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":651,"t":1,"w":314,"b":336,"h":0,"r":"b","dd":0},{"id":652,"t":2,"w":325,"b":338,"h":0,"r":"b","dd":0},{"id":653,"t":3,"w":326,"b":320,"h":0,"r":"b","dd":0},{"id":654,"t":4,"w":319,"b":327,"h":0,"r":"b","dd":0},{"id":655,"t":5,"w":316,"b":312,"h":0,"r":"b","dd":0},{"id":656,"t":6,"w":341,"b":322,"h":0,"r":"b","dd":0},{"id":657,"t":7,"w":337,"b":310,"h":0,"r":"b","dd":0},{"id":658,"t":8,"w":328,"b":317,"h":0,"r":"b","dd":0},{"id":659,"t":9,"w":339,"b":324,"h":0,"r":"b","dd":0},{"id":660,"t":10,"w":315,"b":311,"h":0,"r":"b","dd":0},{"id":661,"t":11,"w":318,"b":313,"h":0,"r":"b","dd":0},{"id":662,"t":12,"w":331,"b":332,"h":0,"r":"b","dd":0},{"id":663,"t":13,"w":340,"b":334,"h":0,"r":"b","dd":0},{"id":664,"t":14,"w":323,"b":333,"h":0,"r":"b","dd":0},{"id":665,"t":15,"w":330,"b":335,"h":0,"r":"b","dd":0},{"id":666,"t":16,"w":321,"b":329,"h":0,"r":"b","dd":0}]"""
        val pairingsR5 = """[{"id":667,"t":1,"w":336,"b":338,"h":0,"r":"b","dd":0},{"id":668,"t":2,"w":320,"b":325,"h":0,"r":"b","dd":0},{"id":669,"t":3,"w":312,"b":327,"h":0,"r":"b","dd":0},{"id":670,"t":4,"w":322,"b":317,"h":0,"r":"b","dd":0},{"id":671,"t":5,"w":314,"b":310,"h":0,"r":"b","dd":0},{"id":672,"t":6,"w":324,"b":326,"h":0,"r":"b","dd":0},{"id":673,"t":7,"w":319,"b":313,"h":0,"r":"b","dd":0},{"id":674,"t":8,"w":328,"b":334,"h":0,"r":"b","dd":0},{"id":675,"t":9,"w":333,"b":341,"h":0,"r":"b","dd":0},{"id":676,"t":10,"w":332,"b":316,"h":0,"r":"b","dd":0},{"id":677,"t":11,"w":311,"b":337,"h":0,"r":"b","dd":0},{"id":678,"t":12,"w":339,"b":340,"h":0,"r":"b","dd":0},{"id":679,"t":13,"w":335,"b":318,"h":0,"r":"b","dd":0},{"id":680,"t":14,"w":331,"b":323,"h":0,"r":"b","dd":0},{"id":681,"t":15,"w":315,"b":329,"h":0,"r":"b","dd":0},{"id":682,"t":16,"w":321,"b":330,"h":0,"r":"b","dd":0}]"""
        val pairingsR6 = """[{"id":683,"t":1,"w":327,"b":325,"h":0,"r":"b","dd":0},{"id":684,"t":2,"w":338,"b":317,"h":0,"r":"b","dd":0},{"id":685,"t":3,"w":310,"b":336,"h":0,"r":"b","dd":0},{"id":686,"t":4,"w":334,"b":312,"h":0,"r":"b","dd":0},{"id":687,"t":5,"w":313,"b":322,"h":0,"r":"b","dd":0},{"id":688,"t":6,"w":316,"b":314,"h":0,"r":"b","dd":0},{"id":689,"t":7,"w":341,"b":326,"h":0,"r":"b","dd":0},{"id":690,"t":8,"w":337,"b":320,"h":0,"r":"b","dd":0},{"id":691,"t":9,"w":332,"b":319,"h":0,"r":"b","dd":0},{"id":692,"t":10,"w":311,"b":328,"h":0,"r":"b","dd":0},{"id":693,"t":11,"w":340,"b":333,"h":0,"r":"b","dd":0},{"id":694,"t":12,"w":324,"b":323,"h":0,"r":"b","dd":0},{"id":695,"t":13,"w":329,"b":318,"h":0,"r":"b","dd":0},{"id":696,"t":14,"w":339,"b":335,"h":0,"r":"b","dd":0},{"id":697,"t":15,"w":315,"b":330,"h":0,"r":"b","dd":0},{"id":698,"t":16,"w":331,"b":321,"h":0,"r":"b","dd":0}]"""
        /*
        2 solutions have the same sum of weights, OpenGotha pairing is
        val pairingsR7 = """[{"id":699,"t":1,"w":336,"b":325,"h":0,"r":"b","dd":0},{"id":700,"t":2,"w":317,"b":312,"h":0,"r":"b","dd":0},{"id":701,"t":3,"w":338,"b":326,"h":0,"r":"b","dd":0},{"id":702,"t":4,"w":314,"b":327,"h":0,"r":"b","dd":0},{"id":703,"t":5,"w":322,"b":310,"h":0,"r":"b","dd":0},{"id":704,"t":6,"w":320,"b":318,"h":0,"r":"b","dd":0},{"id":705,"t":7,"w":341,"b":313,"h":0,"r":"b","dd":0},{"id":706,"t":8,"w":333,"b":319,"h":0,"r":"b","dd":0},{"id":707,"t":9,"w":337,"b":328,"h":0,"r":"b","dd":0},{"id":708,"t":10,"w":334,"b":323,"h":0,"r":"b","dd":0},{"id":709,"t":11,"w":316,"b":329,"h":0,"r":"b","dd":0},{"id":710,"t":12,"w":324,"b":335,"h":0,"r":"b","dd":0},{"id":711,"t":13,"w":330,"b":332,"h":0,"r":"b","dd":0},{"id":712,"t":14,"w":340,"b":311,"h":0,"r":"b","dd":0},{"id":713,"t":15,"w":339,"b":321,"h":0,"r":"b","dd":0},{"id":714,"t":16,"w":331,"b":315,"h":0,"r":"b","dd":0}]"""
        Pairgoth pairing is
         */
        val pairingsR7 = """[{"id":699,"t":1,"w":336,"b":325,"h":0,"r":"?","dd":0},{"id":700,"t":2,"w":338,"b":326,"h":0,"r":"?","dd":1},{"id":701,"t":3,"w":317,"b":320,"h":0,"r":"?","dd":1},{"id":702,"t":4,"w":314,"b":327,"h":0,"r":"?","dd":0},{"id":703,"t":5,"w":322,"b":310,"h":0,"r":"?","dd":0},{"id":704,"t":6,"w":312,"b":318,"h":0,"r":"?","dd":1},{"id":705,"t":7,"w":341,"b":313,"h":0,"r":"?","dd":0},{"id":706,"t":8,"w":333,"b":319,"h":0,"r":"?","dd":0},{"id":707,"t":9,"w":337,"b":328,"h":0,"r":"?","dd":0},{"id":708,"t":10,"w":334,"b":323,"h":0,"r":"?","dd":0},{"id":709,"t":11,"w":316,"b":329,"h":0,"r":"?","dd":1},{"id":710,"t":12,"w":324,"b":335,"h":0,"r":"?","dd":0},{"id":711,"t":13,"w":330,"b":332,"h":0,"r":"?","dd":0},{"id":712,"t":14,"w":340,"b":311,"h":0,"r":"?","dd":0},{"id":713,"t":15,"w":339,"b":321,"h":0,"r":"?","dd":0},{"id":714,"t":16,"w":331,"b":315,"h":0,"r":"?","dd":0}]"""
        val pairings = mutableListOf<String>()
        pairings.add(pairingsR1)
        pairings.add(pairingsR2)
        pairings.add(pairingsR3)
        pairings.add(pairingsR4)
        pairings.add(pairingsR5)
        pairings.add(pairingsR6)
        pairings.add(pairingsR7)

        // read tournament without pairings
        var file = getTestFile("opengotha/pairings/simpleswiss_nopairings.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        assertNotNull(id)
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")

        var games: Json.Array
        var firstGameID: Int

        for (round in 1..7) {
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info("games for round $round: {}", games.toString())
            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/simpleswiss_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            assertTrue(compare_games(games, Json.parse(pairings[round - 1])!!.asArray()),"pairings for round $round differ")
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
    fun `009 not so simple swiss tournament`() {

/*                        // read tournament with pairing
                        var fileOG = getTestFile("opengotha/pairings/notsosimpleswiss_10R.xml")
                        logger.info("read from file $fileOG")
                        val resourceOG = fileOG.readText(StandardCharsets.UTF_8)
                        val respOG = TestAPI.post("/api/tour", resourceOG)
                        val idOG = respOG.asObject().getInt("id")
                        val tournamentOG = TestAPI.get("/api/tour/$idOG").asObject()
                        logger.info(tournamentOG.toString().slice(0..50) + "...")
                        val playersOG = TestAPI.get("/api/tour/$idOG/part").asArray()
                        //logger.info(players.toString().slice(0..50) + "...")
                        //logger.info(playersOG.toString())

                        val pairingsOG = mutableListOf<String>()
                        for (round in 1..tournamentOG.getInt("rounds")!!) {
                            val games = TestAPI.get("/api/tour/$idOG/res/$round").asArray()
                            logger.info("games for round $round: {}", games.toString())
                            pairingsOG.add(games.toString())
                        }*/

        val pairingsR1 = """[{"id":723,"t":1,"w":389,"b":386,"h":0,"r":"b","dd":0},{"id":724,"t":2,"w":373,"b":383,"h":0,"r":"b","dd":0},{"id":725,"t":3,"w":374,"b":370,"h":0,"r":"b","dd":0},{"id":726,"t":4,"w":391,"b":381,"h":0,"r":"b","dd":0},{"id":727,"t":5,"w":369,"b":365,"h":0,"r":"b","dd":0},{"id":728,"t":6,"w":364,"b":371,"h":0,"r":"b","dd":0},{"id":729,"t":7,"w":392,"b":360,"h":0,"r":"b","dd":0},{"id":730,"t":8,"w":362,"b":375,"h":0,"r":"b","dd":0},{"id":731,"t":9,"w":388,"b":363,"h":0,"r":"b","dd":0},{"id":732,"t":10,"w":359,"b":368,"h":0,"r":"b","dd":0},{"id":733,"t":11,"w":387,"b":366,"h":0,"r":"b","dd":0},{"id":734,"t":12,"w":390,"b":372,"h":0,"r":"b","dd":0},{"id":735,"t":13,"w":358,"b":361,"h":0,"r":"b","dd":0},{"id":736,"t":14,"w":367,"b":376,"h":0,"r":"b","dd":0},{"id":737,"t":15,"w":380,"b":379,"h":0,"r":"b","dd":0},{"id":738,"t":16,"w":377,"b":385,"h":0,"r":"b","dd":0},{"id":739,"t":17,"w":384,"b":382,"h":0,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":740,"t":1,"w":381,"b":370,"h":0,"r":"b","dd":0},{"id":741,"t":2,"w":371,"b":365,"h":0,"r":"b","dd":0},{"id":742,"t":3,"w":360,"b":375,"h":0,"r":"b","dd":0},{"id":743,"t":4,"w":372,"b":363,"h":0,"r":"b","dd":0},{"id":744,"t":5,"w":368,"b":376,"h":0,"r":"b","dd":0},{"id":745,"t":6,"w":366,"b":385,"h":0,"r":"b","dd":0},{"id":746,"t":7,"w":386,"b":361,"h":0,"r":"b","dd":0},{"id":747,"t":8,"w":379,"b":383,"h":0,"r":"b","dd":0},{"id":748,"t":9,"w":378,"b":389,"h":0,"r":"b","dd":0},{"id":749,"t":10,"w":373,"b":380,"h":0,"r":"b","dd":0},{"id":750,"t":11,"w":384,"b":391,"h":0,"r":"b","dd":0},{"id":751,"t":12,"w":374,"b":364,"h":0,"r":"b","dd":0},{"id":752,"t":13,"w":369,"b":362,"h":0,"r":"b","dd":0},{"id":753,"t":14,"w":388,"b":392,"h":0,"r":"b","dd":0},{"id":754,"t":15,"w":390,"b":359,"h":0,"r":"b","dd":0},{"id":755,"t":16,"w":367,"b":387,"h":0,"r":"b","dd":0},{"id":756,"t":17,"w":377,"b":358,"h":0,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":757,"t":1,"w":383,"b":370,"h":0,"r":"b","dd":0},{"id":758,"t":2,"w":376,"b":375,"h":0,"r":"b","dd":0},{"id":759,"t":3,"w":361,"b":385,"h":0,"r":"b","dd":0},{"id":760,"t":4,"w":365,"b":363,"h":0,"r":"b","dd":0},{"id":761,"t":5,"w":386,"b":358,"h":0,"r":"b","dd":0},{"id":762,"t":6,"w":372,"b":360,"h":0,"r":"b","dd":0},{"id":763,"t":7,"w":371,"b":391,"h":0,"r":"b","dd":0},{"id":764,"t":8,"w":381,"b":364,"h":0,"r":"b","dd":0},{"id":765,"t":9,"w":368,"b":387,"h":0,"r":"b","dd":0},{"id":766,"t":10,"w":380,"b":366,"h":0,"r":"b","dd":0},{"id":767,"t":11,"w":379,"b":392,"h":0,"r":"b","dd":0},{"id":768,"t":12,"w":359,"b":362,"h":0,"r":"b","dd":0},{"id":769,"t":13,"w":382,"b":378,"h":0,"r":"b","dd":0},{"id":770,"t":14,"w":389,"b":369,"h":0,"r":"b","dd":0},{"id":771,"t":15,"w":367,"b":373,"h":0,"r":"b","dd":0},{"id":772,"t":16,"w":384,"b":388,"h":0,"r":"b","dd":0},{"id":773,"t":17,"w":390,"b":374,"h":0,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":774,"t":1,"w":385,"b":370,"h":0,"r":"b","dd":0},{"id":775,"t":2,"w":375,"b":363,"h":0,"r":"b","dd":0},{"id":776,"t":3,"w":358,"b":376,"h":0,"r":"b","dd":0},{"id":777,"t":4,"w":383,"b":392,"h":0,"r":"b","dd":0},{"id":778,"t":5,"w":364,"b":362,"h":0,"r":"b","dd":0},{"id":779,"t":6,"w":365,"b":387,"h":0,"r":"b","dd":0},{"id":780,"t":7,"w":366,"b":378,"h":0,"r":"b","dd":0},{"id":781,"t":8,"w":361,"b":391,"h":0,"r":"b","dd":0},{"id":782,"t":9,"w":360,"b":382,"h":0,"r":"b","dd":0},{"id":783,"t":10,"w":379,"b":374,"h":0,"r":"b","dd":0},{"id":784,"t":11,"w":372,"b":368,"h":0,"r":"b","dd":0},{"id":785,"t":12,"w":388,"b":377,"h":0,"r":"b","dd":0},{"id":786,"t":13,"w":369,"b":380,"h":0,"r":"b","dd":0},{"id":787,"t":14,"w":371,"b":389,"h":0,"r":"b","dd":0},{"id":788,"t":15,"w":373,"b":386,"h":0,"r":"b","dd":0},{"id":789,"t":16,"w":359,"b":381,"h":0,"r":"b","dd":0},{"id":790,"t":17,"w":390,"b":384,"h":0,"r":"b","dd":0}]"""
        val pairingsR5 = """[{"id":791,"t":1,"w":370,"b":363,"h":0,"r":"b","dd":0},{"id":792,"t":2,"w":362,"b":387,"h":0,"r":"b","dd":0},{"id":793,"t":3,"w":376,"b":378,"h":0,"r":"b","dd":0},{"id":794,"t":4,"w":385,"b":392,"h":0,"r":"b","dd":0},{"id":795,"t":5,"w":375,"b":382,"h":0,"r":"b","dd":0},{"id":796,"t":6,"w":391,"b":366,"h":0,"r":"b","dd":0},{"id":797,"t":7,"w":383,"b":364,"h":0,"r":"b","dd":0},{"id":798,"t":8,"w":368,"b":358,"h":0,"r":"b","dd":0},{"id":799,"t":9,"w":365,"b":386,"h":0,"r":"b","dd":0},{"id":800,"t":10,"w":381,"b":389,"h":0,"r":"b","dd":0},{"id":801,"t":11,"w":374,"b":361,"h":0,"r":"b","dd":0},{"id":802,"t":12,"w":360,"b":377,"h":0,"r":"b","dd":0},{"id":803,"t":13,"w":380,"b":388,"h":0,"r":"b","dd":0},{"id":804,"t":14,"w":373,"b":359,"h":0,"r":"b","dd":0},{"id":805,"t":15,"w":384,"b":372,"h":0,"r":"b","dd":0},{"id":806,"t":16,"w":371,"b":367,"h":0,"r":"b","dd":0},{"id":807,"t":17,"w":369,"b":390,"h":0,"r":"b","dd":0}]"""
        val pairingsR6 = """[{"id":808,"t":1,"w":387,"b":378,"h":0,"r":"b","dd":0},{"id":809,"t":2,"w":392,"b":370,"h":0,"r":"b","dd":0},{"id":810,"t":3,"w":363,"b":382,"h":0,"r":"b","dd":0},{"id":811,"t":4,"w":362,"b":358,"h":0,"r":"b","dd":0},{"id":812,"t":5,"w":376,"b":386,"h":0,"r":"b","dd":0},{"id":813,"t":6,"w":389,"b":361,"h":0,"r":"b","dd":0},{"id":814,"t":7,"w":391,"b":385,"h":0,"r":"b","dd":0},{"id":815,"t":8,"w":364,"b":366,"h":0,"r":"b","dd":0},{"id":816,"t":9,"w":375,"b":377,"h":0,"r":"b","dd":0},{"id":817,"t":10,"w":374,"b":368,"h":0,"r":"b","dd":0},{"id":818,"t":11,"w":372,"b":383,"h":0,"r":"b","dd":0},{"id":819,"t":12,"w":365,"b":379,"h":0,"r":"b","dd":0},{"id":820,"t":13,"w":380,"b":381,"h":0,"r":"b","dd":0},{"id":821,"t":14,"w":359,"b":388,"h":0,"r":"b","dd":0},{"id":822,"t":15,"w":360,"b":367,"h":0,"r":"b","dd":0},{"id":823,"t":16,"w":369,"b":384,"h":0,"r":"b","dd":0},{"id":824,"t":17,"w":371,"b":373,"h":0,"r":"b","dd":0}]"""
        val pairingsR7 = """[{"id":825,"t":1,"w":363,"b":378,"h":0,"r":"b","dd":0},{"id":826,"t":2,"w":370,"b":382,"h":0,"r":"b","dd":0},{"id":827,"t":3,"w":392,"b":366,"h":0,"r":"b","dd":0},{"id":828,"t":4,"w":358,"b":387,"h":0,"r":"b","dd":0},{"id":829,"t":5,"w":386,"b":385,"h":0,"r":"b","dd":0},{"id":830,"t":6,"w":377,"b":391,"h":0,"r":"b","dd":0},{"id":831,"t":7,"w":381,"b":383,"h":0,"r":"b","dd":0},{"id":832,"t":8,"w":368,"b":389,"h":0,"r":"b","dd":0},{"id":833,"t":9,"w":388,"b":362,"h":0,"r":"b","dd":0},{"id":834,"t":10,"w":376,"b":364,"h":0,"r":"b","dd":0},{"id":835,"t":11,"w":375,"b":379,"h":0,"r":"b","dd":0},{"id":836,"t":12,"w":367,"b":380,"h":0,"r":"b","dd":0},{"id":837,"t":13,"w":359,"b":372,"h":0,"r":"b","dd":0},{"id":838,"t":14,"w":365,"b":384,"h":0,"r":"b","dd":0},{"id":839,"t":15,"w":373,"b":374,"h":0,"r":"b","dd":0},{"id":840,"t":16,"w":360,"b":390,"h":0,"r":"b","dd":0},{"id":841,"t":17,"w":371,"b":369,"h":0,"r":"b","dd":0}]"""
        val pairingsR8 = """[{"id":842,"t":1,"w":382,"b":385,"h":0,"r":"b","dd":0},{"id":843,"t":2,"w":378,"b":370,"h":0,"r":"b","dd":0},{"id":844,"t":3,"w":387,"b":363,"h":0,"r":"b","dd":0},{"id":845,"t":4,"w":366,"b":361,"h":0,"r":"b","dd":0},{"id":846,"t":5,"w":383,"b":389,"h":0,"r":"b","dd":0},{"id":847,"t":6,"w":386,"b":364,"h":0,"r":"b","dd":0},{"id":848,"t":7,"w":391,"b":362,"h":0,"r":"b","dd":0},{"id":849,"t":8,"w":392,"b":377,"h":0,"r":"b","dd":0},{"id":850,"t":9,"w":358,"b":379,"h":0,"r":"b","dd":0},{"id":851,"t":10,"w":388,"b":368,"h":0,"r":"b","dd":0},{"id":852,"t":11,"w":384,"b":374,"h":0,"r":"b","dd":0},{"id":853,"t":12,"w":381,"b":372,"h":0,"r":"b","dd":0},{"id":854,"t":13,"w":376,"b":380,"h":0,"r":"b","dd":0},{"id":855,"t":14,"w":375,"b":367,"h":0,"r":"b","dd":0},{"id":856,"t":15,"w":390,"b":365,"h":0,"r":"b","dd":0},{"id":857,"t":16,"w":360,"b":373,"h":0,"r":"b","dd":0},{"id":858,"t":17,"w":369,"b":359,"h":0,"r":"b","dd":0}]"""
        val pairingsR9 = """[{"id":859,"t":1,"w":385,"b":378,"h":0,"r":"b","dd":0},{"id":860,"t":2,"w":363,"b":361,"h":0,"r":"b","dd":0},{"id":861,"t":3,"w":382,"b":387,"h":0,"r":"b","dd":0},{"id":862,"t":4,"w":389,"b":362,"h":0,"r":"b","dd":0},{"id":863,"t":5,"w":370,"b":377,"h":0,"r":"b","dd":0},{"id":864,"t":6,"w":364,"b":379,"h":0,"r":"b","dd":0},{"id":865,"t":7,"w":374,"b":383,"h":0,"r":"b","dd":0},{"id":866,"t":8,"w":391,"b":368,"h":0,"r":"b","dd":0},{"id":867,"t":9,"w":386,"b":372,"h":0,"r":"b","dd":0},{"id":868,"t":10,"w":380,"b":392,"h":0,"r":"b","dd":0},{"id":869,"t":11,"w":358,"b":367,"h":0,"r":"b","dd":0},{"id":870,"t":12,"w":376,"b":384,"h":0,"r":"b","dd":0},{"id":871,"t":13,"w":365,"b":373,"h":0,"r":"b","dd":0},{"id":872,"t":14,"w":375,"b":359,"h":0,"r":"b","dd":0},{"id":873,"t":15,"w":381,"b":390,"h":0,"r":"b","dd":0},{"id":874,"t":16,"w":369,"b":360,"h":0,"r":"b","dd":0},{"id":875,"t":17,"w":388,"b":371,"h":0,"r":"b","dd":0}]"""
        val pairingsR10 = """[{"id":876,"t":1,"w":361,"b":378,"h":0,"r":"b","dd":0},{"id":877,"t":2,"w":385,"b":363,"h":0,"r":"b","dd":0},{"id":878,"t":3,"w":362,"b":382,"h":0,"r":"b","dd":0},{"id":879,"t":4,"w":387,"b":377,"h":0,"r":"b","dd":0},{"id":880,"t":5,"w":370,"b":379,"h":0,"r":"b","dd":0},{"id":881,"t":6,"w":389,"b":364,"h":0,"r":"b","dd":0},{"id":882,"t":7,"w":383,"b":368,"h":0,"r":"b","dd":0},{"id":883,"t":8,"w":372,"b":392,"h":0,"r":"b","dd":0},{"id":884,"t":9,"w":366,"b":367,"h":0,"r":"b","dd":0},{"id":885,"t":10,"w":380,"b":386,"h":0,"r":"b","dd":0},{"id":886,"t":11,"w":373,"b":391,"h":0,"r":"b","dd":0},{"id":887,"t":12,"w":359,"b":374,"h":0,"r":"b","dd":0},{"id":888,"t":13,"w":358,"b":390,"h":0,"r":"b","dd":0},{"id":889,"t":14,"w":384,"b":375,"h":0,"r":"b","dd":0},{"id":890,"t":15,"w":360,"b":376,"h":0,"r":"b","dd":0},{"id":891,"t":16,"w":388,"b":365,"h":0,"r":"b","dd":0},{"id":892,"t":17,"w":381,"b":371,"h":0,"r":"b","dd":0}]"""

        val pairings = mutableListOf<String>()
        pairings.add(pairingsR1)
        pairings.add(pairingsR2)
        pairings.add(pairingsR3)
        pairings.add(pairingsR4)
        pairings.add(pairingsR5)
        pairings.add(pairingsR6)
        pairings.add(pairingsR7)
        pairings.add(pairingsR8)
        pairings.add(pairingsR9)
        pairings.add(pairingsR10)

        // read tournament without pairings
        var file = getTestFile("opengotha/pairings/notsosimpleswiss_nopairings.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        assertNotNull(id)
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")
        logger.info(players.toString())

        var games: Json.Array
        var firstGameID: Int
        var playersList = mutableListOf<Long>()

        for (i in 0..34){
            playersList.add(players.getJson(i)!!.asObject()["id"] as Long)
        }

        for (round in 1..10) {
            //games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array(playersList.filter{it != byePlayerList[round-1]})).asArray()
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))

            //games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array(playersList.filter{it != byePlayerList[round-1]})).asArray()
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info("games for round $round: {}", games.toString())

            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/notsosimpleswiss_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            assertTrue(compare_games(games, Json.parse(pairings[round - 1])!!.asArray()),"pairings for round $round differ")
            logger.info("Pairings for round $round match OpenGotha")

            logger.info("games for round $round: {}", games.toString())

            firstGameID = (games.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
            for (gameID in firstGameID..firstGameID + 16) {
                resp = TestAPI.put("/api/tour/$id/res/$round", Json.parse("""{"id":$gameID,"result":"b"}""")).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }
            logger.info("Results succesfully entered for round $round")
        }

    }

    @Test
    fun `testSimpleMM`() {
        /*
        // read tournament with pairing
        var fileOG = getTestFile("opengotha/pairings/simplemm.xml")

        logger.info("read from file $fileOG")
        val resourceOG = fileOG.readText(StandardCharsets.UTF_8)
        val respOG = TestAPI.post("/api/tour", resourceOG)
        val idOG = respOG.asObject().getInt("id")
        val tournamentOG = TestAPI.get("/api/tour/$idOG").asObject()
        //logger.info(tournamentOG.toString().slice(0..50) + "...")
        val playersOG = TestAPI.get("/api/tour/$idOG/part").asArray()
        //logger.info(players.toString().slice(0..50) + "...")
        logger.info(playersOG.toString())

        val pairingsOG = mutableListOf<Json.Array>()
        for (round in 1..tournamentOG.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$idOG/res/$round").asArray()
            logger.info("games for round $round: {}", games.toString())
            pairingsOG.add(games)
        }
        */

        //assert(false)


        val pairingsR1 = """[{"id":1,"w":3,"b":5,"h":0,"r":"w","dd":0},{"id":2,"w":12,"b":10,"h":0,"r":"b","dd":0},{"id":3,"w":9,"b":14,"h":0,"r":"b","dd":0},{"id":4,"w":11,"b":6,"h":0,"r":"b","dd":0},{"id":5,"w":13,"b":15,"h":0,"r":"b","dd":0},{"id":6,"w":2,"b":16,"h":1,"r":"w","dd":0},{"id":7,"w":8,"b":4,"h":5,"r":"b","dd":0},{"id":8,"w":7,"b":1,"h":2,"r":"w","dd":0}]"""
        val pairingsR2 = """[{"id":9,"w":14,"b":3,"h":0,"r":"b","dd":0},{"id":10,"w":10,"b":5,"h":0,"r":"b","dd":0},{"id":11,"w":6,"b":9,"h":0,"r":"b","dd":0},{"id":12,"w":15,"b":12,"h":0,"r":"w","dd":0},{"id":13,"w":2,"b":11,"h":0,"r":"w","dd":0},{"id":14,"w":8,"b":13,"h":0,"r":"b","dd":0},{"id":15,"w":7,"b":4,"h":0,"r":"b","dd":0},{"id":16,"w":16,"b":1,"h":7,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":17,"w":5,"b":14,"h":0,"r":"b","dd":0},{"id":18,"w":10,"b":9,"h":0,"r":"w","dd":0},{"id":19,"w":15,"b":3,"h":0,"r":"w","dd":0},{"id":20,"w":12,"b":2,"h":0,"r":"b","dd":0},{"id":21,"w":6,"b":13,"h":0,"r":"b","dd":0},{"id":22,"w":11,"b":8,"h":0,"r":"w","dd":0},{"id":23,"w":16,"b":7,"h":3,"r":"w","dd":0},{"id":24,"w":4,"b":1,"h":3,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":25,"w":3,"b":10,"h":0,"r":"w","dd":0},{"id":26,"w":14,"b":15,"h":0,"r":"b","dd":0},{"id":27,"w":5,"b":2,"h":0,"r":"w","dd":0},{"id":28,"w":12,"b":6,"h":0,"r":"w","dd":0},{"id":29,"w":9,"b":11,"h":0,"r":"w","dd":0},{"id":30,"w":16,"b":4,"h":3,"r":"b","dd":0},{"id":31,"w":13,"b":7,"h":5,"r":"w","dd":0},{"id":32,"w":8,"b":1,"h":6,"r":"w","dd":0}]"""
        val pairingsR5 = """[{"id":33,"w":15,"b":5,"h":0,"r":"w","dd":0},{"id":34,"w":14,"b":10,"h":0,"r":"b","dd":0},{"id":35,"w":9,"b":3,"h":0,"r":"w","dd":0},{"id":36,"w":13,"b":2,"h":0,"r":"w","dd":0},{"id":37,"w":16,"b":12,"h":0,"r":"b","dd":0},{"id":38,"w":11,"b":4,"h":3,"r":"b","dd":0},{"id":39,"w":8,"b":7,"h":5,"r":"w","dd":0},{"id":40,"w":6,"b":1,"h":7,"r":"b","dd":0}]"""
        val pairings = mutableListOf<String>()
        pairings.add(pairingsR1)
        pairings.add(pairingsR2)
        pairings.add(pairingsR3)
        pairings.add(pairingsR4)
        pairings.add(pairingsR5)

        // read tournament without pairings
        var file = getTestFile("opengotha/pairings/simplemm_nopairings.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        assertNotNull(id)
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")

        var games: Json.Array
        var firstGameID: Int
        var forcedGames: Json.Array
        var game: Json

        for (round in 1..5) {
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))
            // games must be created and then modified by PUT
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info(games.toString())
            val skipSeeding = round <= 2
            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/simplemm/simplemm_weights_R$round.txt"), skipSeeding), "Not matching opengotha weights for round $round")
            logger.info("Weights for round $round match OpenGotha")
            assertTrue(compare_games(games, Json.parse(pairings[round - 1])!!.asArray(), skipColor=true),"pairings for round $round differ")
            logger.info("Pairing for round $round match OpenGotha")

            forcedGames = Json.parse(pairings[round-1])!!.asArray()
            //forcedGames = pairingsOG[round-1]

            for (j in 0..forcedGames.size-1) {
                game = forcedGames.getJson(j)!!.asObject()
                TestAPI.put("/api/tour/$id/pair/$round", game)
            }


            // Enter results
            firstGameID = (games.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
            // Extract results
            val results = forcedGames.map { game -> game.toString().split("r\":\"")[1][0] }
            for (j in 0 .. forcedGames.size-1) {
                resp = TestAPI.put("/api/tour/$id/res/$round", Json.parse("""{"id":${firstGameID + j},"result":"${results[j]}"}""")).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }

            logger.info("Results succesfully entered for round $round")
        }
    }

    @Test
    fun `testNotSimpleMM`() {
        /*
        // read tournament with pairing
        var fileOG = getTestFile("opengotha/pairings/notsimplemm.xml")

        logger.info("read from file $fileOG")
        val resourceOG = fileOG.readText(StandardCharsets.UTF_8)
        val respOG = TestAPI.post("/api/tour", resourceOG)
        val idOG = respOG.asObject().getInt("id")
        val tournamentOG = TestAPI.get("/api/tour/$idOG").asObject()
        //logger.info(tournamentOG.toString().slice(0..50) + "...")
        val playersOG = TestAPI.get("/api/tour/$idOG/part").asArray()
        //logger.info(players.toString().slice(0..50) + "...")
        logger.info(playersOG.toString())

        val pairingsOG = mutableListOf<Json.Array>()
        for (round in 1..tournamentOG.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$idOG/res/$round").asArray()
            logger.info("games for round $round: {}", games.toString())
            pairingsOG.add(games)
        }
        */

        //assert(false)


        val pairingsR1 = """[{"id":1,"t":1,"w":8,"b":1,"h":0,"r":"b","dd":0},{"id":2,"t":2,"w":3,"b":6,"h":0,"r":"b","dd":0},{"id":3,"t":3,"w":5,"b":2,"h":5,"r":"w","dd":0},{"id":4,"t":4,"w":9,"b":7,"h":1,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":5,"t":1,"w":6,"b":5,"h":0,"r":"w","dd":0},{"id":6,"t":2,"w":3,"b":8,"h":0,"r":"w","dd":0},{"id":7,"t":3,"w":9,"b":2,"h":0,"r":"w","dd":0},{"id":8,"t":4,"w":7,"b":4,"h":3,"r":"w","dd":0}]"""
        val pairingsR3 = """[{"id":9,"t":1,"w":5,"b":3,"h":0,"r":"w","dd":0},{"id":10,"t":2,"w":1,"b":6,"h":0,"r":"w","dd":0},{"id":11,"t":3,"w":9,"b":4,"h":5,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":12,"t":1,"w":5,"b":1,"h":0,"r":"w","dd":0},{"id":13,"t":2,"w":6,"b":9,"h":3,"r":"w","dd":0},{"id":14,"t":3,"w":3,"b":7,"h":3,"r":"b","dd":0},{"id":15,"t":4,"w":2,"b":4,"h":2,"r":"w","dd":0}]"""
        val pairingsR5 = """[{"id":16,"t":1,"w":6,"b":8,"h":0,"r":"w","dd":0},{"id":17,"t":2,"w":5,"b":7,"h":2,"r":"w","dd":0},{"id":18,"t":3,"w":1,"b":9,"h":3,"r":"w","dd":0},{"id":19,"t":4,"w":3,"b":4,"h":8,"r":"b","dd":0}]"""
        val pairings = mutableListOf<String>()
        pairings.add(pairingsR1)
        pairings.add(pairingsR2)
        pairings.add(pairingsR3)
        pairings.add(pairingsR4)
        pairings.add(pairingsR5)

        // read tournament without pairings
        var file = getTestFile("opengotha/pairings/notsimplemm_nopairings.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        var resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        assertNotNull(id)
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        logger.info(players.toString().slice(0..50) + "...")

        var games: Json.Array
        var firstGameID: Int
        var forcedGames: Json.Array
        var game: Json

        for (round in 1..5) {
            BaseSolver.weightsLogger = PrintWriter(FileWriter(getOutputFile("weights.txt")))
            // games must be created and then modified by PUT
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info(games.toString())
            logger.info(pairings[round-1])
            assertTrue(compare_weights(getOutputFile("weights.txt"), getTestFile("opengotha/notsimplemm/notsimplemm_weights_R$round.txt")), "Not matching opengotha weights for round $round")
            logger.info("Weights for round $round match OpenGotha")
            assertTrue(compare_games(games, Json.parse(pairings[round - 1])!!.asArray(), skipColor=true),"pairings for round $round differ")
            logger.info("Pairing for round $round match OpenGotha")

            forcedGames = Json.parse(pairings[round-1])!!.asArray()
            //forcedGames = pairingsOG[round-1]

            for (j in 0..forcedGames.size-1) {
                game = forcedGames.getJson(j)!!.asObject()
                TestAPI.put("/api/tour/$id/pair/$round", game)
            }


            // Enter results
            firstGameID = (games.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
            // Extract results
            val results = forcedGames.map { game -> game.toString().split("r\":\"")[1][0] }
            for (j in 0 .. forcedGames.size-1) {
                resp = TestAPI.put("/api/tour/$id/res/$round", Json.parse("""{"id":${firstGameID + j},"result":"${results[j]}"}""")).asObject()
                assertTrue(resp.getBoolean("success") == true, "expecting success")
            }

            logger.info("Results succesfully entered for round $round")
        }
    }

}