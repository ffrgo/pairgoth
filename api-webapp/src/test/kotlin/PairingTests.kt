package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.fromJson
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestMethodOrder(MethodName::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PairingTests: TestBase() {

    fun compare_weights(file1:String, file2:String):Boolean {

        // Maps to store name pairs and costs
        val map1 = HashMap<Pair<String, String>, List<Double>>()
        val map2 = HashMap<Pair<String, String>, List<Double>>()
        var count: Int = 1

        for (file in listOf(file1, file2)) {

            // Read lines
            val lines = getTestFile(file).readLines()

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
                if (abs(value!![9] - map2[key]!![9])>10 && identical==true) {
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

    fun compare_games(games:Json.Array, opengotha:Json.Array): Boolean{
        if (games.size != opengotha.size) return false
        val gamesPair = mutableSetOf<Pair<ID,ID>>()
        val openGothaPair = mutableSetOf<Pair<ID,ID>>()
        for (i in 0 until games.size) {
            val tmp = Game.fromJson(games.getJson(i)!!.asObject())
            gamesPair.add(Pair(tmp.white, tmp.black))
            val tmpOG = Game.fromJson(opengotha.getJson(i)!!.asObject())
            openGothaPair.add(Pair(tmpOG.white, tmpOG.black))
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
                var fileOG = getTestFile("opengotha/tournamentfiles/simpleswiss_7R.xml")
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

        val pairingsR1 = """[{"id":937,"w":519,"b":524,"h":0,"r":"b","dd":0},{"id":938,"w":510,"b":508,"h":0,"r":"b","dd":0},{"id":939,"w":526,"b":518,"h":0,"r":"b","dd":0},{"id":940,"w":507,"b":503,"h":0,"r":"b","dd":0},{"id":941,"w":527,"b":502,"h":0,"r":"b","dd":0},{"id":942,"w":498,"b":511,"h":0,"r":"b","dd":0},{"id":943,"w":501,"b":500,"h":0,"r":"b","dd":0},{"id":944,"w":517,"b":523,"h":0,"r":"b","dd":0},{"id":945,"w":497,"b":512,"h":0,"r":"b","dd":0},{"id":946,"w":506,"b":522,"h":0,"r":"b","dd":0},{"id":947,"w":509,"b":504,"h":0,"r":"b","dd":0},{"id":948,"w":496,"b":525,"h":0,"r":"b","dd":0},{"id":949,"w":499,"b":513,"h":0,"r":"b","dd":0},{"id":950,"w":516,"b":505,"h":0,"r":"b","dd":0},{"id":951,"w":515,"b":520,"h":0,"r":"b","dd":0},{"id":952,"w":521,"b":514,"h":0,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":953,"w":520,"b":524,"h":0,"r":"b","dd":0},{"id":954,"w":518,"b":508,"h":0,"r":"b","dd":0},{"id":955,"w":503,"b":511,"h":0,"r":"b","dd":0},{"id":956,"w":502,"b":512,"h":0,"r":"b","dd":0},{"id":957,"w":504,"b":500,"h":0,"r":"b","dd":0},{"id":958,"w":525,"b":523,"h":0,"r":"b","dd":0},{"id":959,"w":505,"b":522,"h":0,"r":"b","dd":0},{"id":960,"w":514,"b":513,"h":0,"r":"b","dd":0},{"id":961,"w":526,"b":510,"h":0,"r":"b","dd":0},{"id":962,"w":507,"b":498,"h":0,"r":"b","dd":0},{"id":963,"w":497,"b":527,"h":0,"r":"b","dd":0},{"id":964,"w":509,"b":501,"h":0,"r":"b","dd":0},{"id":965,"w":517,"b":496,"h":0,"r":"b","dd":0},{"id":966,"w":516,"b":506,"h":0,"r":"b","dd":0},{"id":967,"w":521,"b":499,"h":0,"r":"b","dd":0},{"id":968,"w":515,"b":519,"h":0,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":969,"w":513,"b":524,"h":0,"r":"b","dd":0},{"id":970,"w":508,"b":511,"h":0,"r":"b","dd":0},{"id":971,"w":523,"b":500,"h":0,"r":"b","dd":0},{"id":972,"w":512,"b":522,"h":0,"r":"b","dd":0},{"id":973,"w":504,"b":503,"h":0,"r":"b","dd":0},{"id":974,"w":499,"b":498,"h":0,"r":"b","dd":0},{"id":975,"w":520,"b":505,"h":0,"r":"b","dd":0},{"id":976,"w":519,"b":514,"h":0,"r":"b","dd":0},{"id":977,"w":501,"b":502,"h":0,"r":"b","dd":0},{"id":978,"w":525,"b":506,"h":0,"r":"b","dd":0},{"id":979,"w":510,"b":496,"h":0,"r":"b","dd":0},{"id":980,"w":518,"b":527,"h":0,"r":"b","dd":0},{"id":981,"w":507,"b":526,"h":0,"r":"b","dd":0},{"id":982,"w":515,"b":509,"h":0,"r":"b","dd":0},{"id":983,"w":516,"b":497,"h":0,"r":"b","dd":0},{"id":984,"w":521,"b":517,"h":0,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":985,"w":500,"b":522,"h":0,"r":"b","dd":0},{"id":986,"w":511,"b":524,"h":0,"r":"b","dd":0},{"id":987,"w":512,"b":506,"h":0,"r":"b","dd":0},{"id":988,"w":505,"b":513,"h":0,"r":"b","dd":0},{"id":989,"w":502,"b":498,"h":0,"r":"b","dd":0},{"id":990,"w":527,"b":508,"h":0,"r":"b","dd":0},{"id":991,"w":523,"b":496,"h":0,"r":"b","dd":0},{"id":992,"w":514,"b":503,"h":0,"r":"b","dd":0},{"id":993,"w":525,"b":510,"h":0,"r":"b","dd":0},{"id":994,"w":501,"b":497,"h":0,"r":"b","dd":0},{"id":995,"w":504,"b":499,"h":0,"r":"b","dd":0},{"id":996,"w":517,"b":518,"h":0,"r":"b","dd":0},{"id":997,"w":526,"b":520,"h":0,"r":"b","dd":0},{"id":998,"w":509,"b":519,"h":0,"r":"b","dd":0},{"id":999,"w":516,"b":521,"h":0,"r":"b","dd":0},{"id":1000,"w":507,"b":515,"h":0,"r":"b","dd":0}]"""
        val pairingsR5 = """[{"id":1001,"w":522,"b":524,"h":0,"r":"b","dd":0},{"id":1002,"w":506,"b":511,"h":0,"r":"b","dd":0},{"id":1003,"w":498,"b":513,"h":0,"r":"b","dd":0},{"id":1004,"w":508,"b":503,"h":0,"r":"b","dd":0},{"id":1005,"w":500,"b":496,"h":0,"r":"b","dd":0},{"id":1006,"w":510,"b":512,"h":0,"r":"b","dd":0},{"id":1007,"w":505,"b":499,"h":0,"r":"b","dd":0},{"id":1008,"w":514,"b":520,"h":0,"r":"b","dd":0},{"id":1009,"w":519,"b":527,"h":0,"r":"b","dd":0},{"id":1010,"w":518,"b":502,"h":0,"r":"b","dd":0},{"id":1011,"w":497,"b":523,"h":0,"r":"b","dd":0},{"id":1012,"w":525,"b":526,"h":0,"r":"b","dd":0},{"id":1013,"w":521,"b":504,"h":0,"r":"b","dd":0},{"id":1014,"w":517,"b":509,"h":0,"r":"b","dd":0},{"id":1015,"w":501,"b":515,"h":0,"r":"b","dd":0},{"id":1016,"w":507,"b":516,"h":0,"r":"b","dd":0}]"""
        val pairingsR6 = """[{"id":1017,"w":513,"b":511,"h":0,"r":"b","dd":0},{"id":1018,"w":524,"b":503,"h":0,"r":"b","dd":0},{"id":1019,"w":496,"b":522,"h":0,"r":"b","dd":0},{"id":1020,"w":520,"b":498,"h":0,"r":"b","dd":0},{"id":1021,"w":499,"b":508,"h":0,"r":"b","dd":0},{"id":1022,"w":502,"b":500,"h":0,"r":"b","dd":0},{"id":1023,"w":527,"b":512,"h":0,"r":"b","dd":0},{"id":1024,"w":523,"b":506,"h":0,"r":"b","dd":0},{"id":1025,"w":518,"b":505,"h":0,"r":"b","dd":0},{"id":1026,"w":497,"b":514,"h":0,"r":"b","dd":0},{"id":1027,"w":526,"b":519,"h":0,"r":"b","dd":0},{"id":1028,"w":510,"b":509,"h":0,"r":"b","dd":0},{"id":1029,"w":515,"b":504,"h":0,"r":"b","dd":0},{"id":1030,"w":525,"b":521,"h":0,"r":"b","dd":0},{"id":1031,"w":501,"b":516,"h":0,"r":"b","dd":0},{"id":1032,"w":517,"b":507,"h":0,"r":"b","dd":0}]"""
        /*
        2 solutions have the same sum of weights, OpenGotha pairing is
        val pairingsR7 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":498,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":506,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
         */
        val pairingsR7 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":506,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":498,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
        val pairings = mutableListOf<String>()
        pairings.add(pairingsR1)
        pairings.add(pairingsR2)
        pairings.add(pairingsR3)
        pairings.add(pairingsR4)
        pairings.add(pairingsR5)
        pairings.add(pairingsR6)
        pairings.add(pairingsR7)

        // read tournament without pairings
        var file = getTestFile("opengotha/tournamentfiles/simpleswiss_nopairings.xml")
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
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            logger.info("games for round $round: {}", games.toString())

            assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R$round.txt"), "Not matching opengotha weights for round $round")
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

}