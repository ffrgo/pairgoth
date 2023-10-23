package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.fromJson
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@TestMethodOrder(MethodName::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicTests: TestBase() {

    val aTournament = Json.Object(
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
        "rounds" to 2,
        "pairing" to Json.Object(
            "type" to "SWISS",
            "method" to "SPLIT_AND_SLIP"
        )
    )

    val aSimpleSwissTournament = Json.Object(
        "type" to "INDIVIDUAL",
        "name" to "Simple Swiss",
        "shortName" to "simple-swiss",
        "startDate" to "2023-05-10",
        "endDate" to "2023-05-12",
        "country" to "FR",
        "location" to "Grenoble",
        "online" to false,
        "timeSystem" to Json.Object(
            "type" to "FISCHER",
            "mainTime" to 1800,
            "increment" to 15
        ),
        "rounds" to 4,
        "pairing" to Json.Object(
            "type" to "SWISS",
            "method" to "SPLIT_AND_SLIP"
        )
    )

    val aTeamTournament = Json.Object(
        "type" to "TEAM2",
        "name" to "Mon Tournoi par équipes",
        "shortName" to "mon-tournoi-par-equipes",
        "startDate" to "2023-05-20",
        "endDate" to "2023-05-23",
        "country" to "FR",
        "location" to "Marseille",
        "online" to true,
        "timeSystem" to Json.Object(
            "type" to "FISCHER",
            "mainTime" to 1200,
            "increment" to 10
        ),
        "rounds" to 2,
        "pairing" to Json.Object(
            "type" to "MAC_MAHON"
        )
    )

    val aPlayer = Json.Object(
        "name" to "Burma",
        "firstname" to "Nestor",
        "rating" to 1600,
        "rank" to -5,
        "country" to "FR",
        "club" to "13Ma"
    )

    val anotherPlayer = Json.Object(
        "name" to "Poirot",
        "firstname" to "Hercule",
        "rating" to 1700,
        "rank" to -1,
        "country" to "FR",
        "club" to "75Op"
    )

    var aTournamentID: ID? = null
    var aTeamTournamentID: ID? = null
    var aPlayerID: ID? = null
    var anotherPlayerID: ID? = null
    var aTournamentGameID: ID? = null
    
    @Test
    fun `001 create tournament`() {
        val resp = TestAPI.post("/api/tour", aTournament).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aTournamentID = resp.getInt("id")
        assertNotNull(aTournamentID)
    }

    @Test
    fun `002 get tournament`() {
        val resp = TestAPI.get("/api/tour/$aTournamentID").asObject()
        assertEquals(aTournamentID, resp.getInt("id"), "First tournament should have id #$aTournamentID")
        // filter out "id", and also "komi", "rules" and "gobanSize" which were provided by default
        // also filter out "pairing", which is filled by all default values
        val cmp = Json.Object(*resp.entries.filter { it.key !in listOf("id", "komi", "rules", "gobanSize", "pairing") }.map { Pair(it.key, it.value) }.toTypedArray())
        val expected = aTournament.entries.filter { it.key != "pairing" }.map { Pair(it.key, it.value) }.toMap().toJsonObject()
        assertEquals(expected.toString(), cmp.toString(), "tournament differs")
    }

    @Test
    fun `003 register user`() {
        val resp = TestAPI.post("/api/tour/$aTournamentID/part", aPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aPlayerID = resp.getInt("id")
        val players = TestAPI.get("/api/tour/$aTournamentID/part").asArray()
        val player = players[0] as Json.Object
        assertEquals(aPlayerID, player.getInt("id"), "First player should have id #$aPlayerID")
        // filter out "id"
        val cmp = Json.Object(*player.entries.filter { it.key != "id" }.map { Pair(it.key, it.value) }.toTypedArray())
        assertEquals(aPlayer.toString(), cmp.toString(), "player differs")
    }

    @Test
    fun `004 modify user`() {
        // remove player aPlayer from round #2
        val resp = TestAPI.put("/api/tour/$aTournamentID/part/$aPlayerID", Json.Object("skip" to Json.Array(2))).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val player = TestAPI.get("/api/tour/$aTournamentID/part/$aPlayerID").asObject()
        assertEquals("[2]", player.getArray("skip").toString(), "First player should skip round #2")
    }

    @Test
    fun `005 pair`() {
        val resp = TestAPI.post("/api/tour/$aTournamentID/part", anotherPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        anotherPlayerID = resp.getInt("id")
        var games = TestAPI.post("/api/tour/$aTournamentID/pair/1", Json.Array("all")).asArray()
        aTournamentGameID = (games[0] as Json.Object).getInt("id")
        val possibleResults = setOf(
            """[{"id":$aTournamentGameID,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"?","dd":0}]""",
            """[{"id":$aTournamentGameID,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"?","dd":0}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "pairing differs")
        games = TestAPI.get("/api/tour/$aTournamentID/res/1").asArray()
        assertTrue(possibleResults.contains(games.toString()), "results differs")
        val empty = TestAPI.get("/api/tour/$aTournamentID/pair/1").asArray()
        assertEquals("[]", empty.toString(), "no more pairables for round 1")
    }

    @Test
    fun `006 result`() {
        val resp = TestAPI.put("/api/tour/$aTournamentID/res/1", Json.parse("""{"id":$aTournamentGameID,"result":"b"}""")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val games = TestAPI.get("/api/tour/$aTournamentID/res/1")
        val possibleResults = setOf(
            """[{"id":$aTournamentGameID,"w":$aPlayerID,"b":$anotherPlayerID,"h":0,"r":"b","dd":0}]""",
            """[{"id":$aTournamentGameID,"w":$anotherPlayerID,"b":$aPlayerID,"h":0,"r":"b","dd":0}]"""
        )
        assertTrue(possibleResults.contains(games.toString()), "results differ")
    }

    @Test
    fun `007 team tournament, MacMahon`() {
        var resp = TestAPI.post("/api/tour", aTeamTournament).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        aTeamTournamentID = resp.getInt("id")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", aPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/part", anotherPlayer).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamPlayerID = resp.getInt("id") ?: fail("id cannot be null")
        var arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[]", arr.toString(), "expecting an empty array")
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Buffallos", "players":[$aTeamPlayerID, $anotherTeamPlayerID] }""")?.asObject() ?: fail("no null allowed here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val aTeamID = resp.getInt("id") ?: error("no null allowed here")
        resp = TestAPI.get("/api/tour/$aTeamTournamentID/team/$aTeamID").asObject()
        assertEquals("""{"id":$aTeamID,"name":"The Buffallos","players":[$aTeamPlayerID,$anotherTeamPlayerID]}""", resp.toString(), "expecting team description")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[$aTeamID]", arr.toString(), "expecting a singleton array")
        // nothing stops us in reusing players in different teams, at least for now...
        resp = TestAPI.post("/api/tour/$aTeamTournamentID/team", Json.parse("""{ "name":"The Billies", "players":[$aTeamPlayerID, $anotherTeamPlayerID] }""")?.asObject() ?: fail("no null here")).asObject()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        val anotherTeamID = resp.getInt("id") ?: fail("no null here")
        arr = TestAPI.get("/api/tour/$aTeamTournamentID/pair/1").asArray()
        assertEquals("[$aTeamID,$anotherTeamID]", arr.toString(), "expecting two pairables")
        arr = TestAPI.post("/api/tour/$aTeamTournamentID/pair/1", Json.parse("""["all"]""")).asArray()
        assertTrue(resp.getBoolean("success") == true, "expecting success")
        // TODO check pairing
        // val expected = """"["id":1,"w":5,"b":6,"h":3,"r":"?"]"""
    }

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

/*        // read tournament with pairing
        var file = getTestFile("opengotha/tournamentfiles/simpleswiss_7R.xml")
        logger.info("read from file $file")
        val resource = file.readText(StandardCharsets.UTF_8)
        val resp = TestAPI.post("/api/tour", resource)
        val id = resp.asObject().getInt("id")
        val tournament = TestAPI.get("/api/tour/$id").asObject()
        logger.info(tournament.toString().slice(0..50) + "...")
        val players = TestAPI.get("/api/tour/$id/part").asArray()
        //logger.info(players.toString().slice(0..50) + "...")
        logger.info(players.toString())

        for (round in 1..tournament.getInt("rounds")!!) {
            val games = TestAPI.get("/api/tour/$id/res/$round").asArray()
            logger.info("games for round $round: {}", games.toString())
            val players = TestAPI.get("/api/tour/$id/part").asArray()
            //logger.info(players.toString().slice(0..500) + "...")
        }*/

        val pairings_R1 = """[{"id":939,"w":525,"b":530,"h":0,"r":"?","dd":0},{"id":940,"w":516,"b":514,"h":0,"r":"?","dd":0},{"id":941,"w":532,"b":524,"h":0,"r":"?","dd":0},{"id":942,"w":513,"b":509,"h":0,"r":"?","dd":0},{"id":943,"w":533,"b":508,"h":0,"r":"?","dd":0},{"id":944,"w":504,"b":517,"h":0,"r":"?","dd":0},{"id":945,"w":507,"b":506,"h":0,"r":"?","dd":0},{"id":946,"w":523,"b":529,"h":0,"r":"?","dd":0},{"id":947,"w":503,"b":518,"h":0,"r":"?","dd":0},{"id":948,"w":512,"b":528,"h":0,"r":"?","dd":0},{"id":949,"w":515,"b":510,"h":0,"r":"?","dd":0},{"id":950,"w":502,"b":531,"h":0,"r":"?","dd":0},{"id":951,"w":505,"b":519,"h":0,"r":"?","dd":0},{"id":952,"w":522,"b":511,"h":0,"r":"?","dd":0},{"id":953,"w":521,"b":526,"h":0,"r":"?","dd":0},{"id":954,"w":527,"b":520,"h":0,"r":"?","dd":0}]"""
        val pairings_R2 = """[{"id":955,"w":526,"b":530,"h":0,"r":"?","dd":0},{"id":956,"w":524,"b":514,"h":0,"r":"?","dd":0},{"id":957,"w":509,"b":517,"h":0,"r":"?","dd":0},{"id":958,"w":508,"b":518,"h":0,"r":"?","dd":0},{"id":959,"w":510,"b":506,"h":0,"r":"?","dd":0},{"id":960,"w":531,"b":529,"h":0,"r":"?","dd":0},{"id":961,"w":511,"b":528,"h":0,"r":"?","dd":0},{"id":962,"w":520,"b":519,"h":0,"r":"?","dd":0},{"id":963,"w":532,"b":516,"h":0,"r":"?","dd":0},{"id":964,"w":513,"b":504,"h":0,"r":"?","dd":0},{"id":965,"w":503,"b":533,"h":0,"r":"?","dd":0},{"id":966,"w":515,"b":507,"h":0,"r":"?","dd":0},{"id":967,"w":523,"b":502,"h":0,"r":"?","dd":0},{"id":968,"w":522,"b":512,"h":0,"r":"?","dd":0},{"id":969,"w":527,"b":505,"h":0,"r":"?","dd":0},{"id":970,"w":521,"b":525,"h":0,"r":"?","dd":0}]"""
        val pairings_R3 = """[{"id":971,"w":519,"b":530,"h":0,"r":"?","dd":0},{"id":972,"w":514,"b":517,"h":0,"r":"?","dd":0},{"id":973,"w":529,"b":506,"h":0,"r":"?","dd":0},{"id":974,"w":518,"b":528,"h":0,"r":"?","dd":0},{"id":975,"w":510,"b":509,"h":0,"r":"?","dd":0},{"id":976,"w":505,"b":504,"h":0,"r":"?","dd":0},{"id":977,"w":526,"b":511,"h":0,"r":"?","dd":0},{"id":978,"w":525,"b":520,"h":0,"r":"?","dd":0},{"id":979,"w":507,"b":508,"h":0,"r":"?","dd":0},{"id":980,"w":531,"b":512,"h":0,"r":"?","dd":0},{"id":981,"w":516,"b":502,"h":0,"r":"?","dd":0},{"id":982,"w":524,"b":533,"h":0,"r":"?","dd":0},{"id":983,"w":513,"b":532,"h":0,"r":"?","dd":0},{"id":984,"w":521,"b":515,"h":0,"r":"?","dd":0},{"id":985,"w":522,"b":503,"h":0,"r":"?","dd":0},{"id":986,"w":527,"b":523,"h":0,"r":"?","dd":0}]"""
        val pairings_R4 = """[{"id":987,"w":506,"b":528,"h":0,"r":"?","dd":0},{"id":988,"w":517,"b":530,"h":0,"r":"?","dd":0},{"id":989,"w":518,"b":512,"h":0,"r":"?","dd":0},{"id":990,"w":511,"b":519,"h":0,"r":"?","dd":0},{"id":991,"w":508,"b":504,"h":0,"r":"?","dd":0},{"id":992,"w":533,"b":514,"h":0,"r":"?","dd":0},{"id":993,"w":529,"b":502,"h":0,"r":"?","dd":0},{"id":994,"w":520,"b":509,"h":0,"r":"?","dd":0},{"id":995,"w":531,"b":516,"h":0,"r":"?","dd":0},{"id":996,"w":507,"b":503,"h":0,"r":"?","dd":0},{"id":997,"w":510,"b":505,"h":0,"r":"?","dd":0},{"id":998,"w":523,"b":524,"h":0,"r":"?","dd":0},{"id":999,"w":532,"b":526,"h":0,"r":"?","dd":0},{"id":1000,"w":515,"b":525,"h":0,"r":"?","dd":0},{"id":1001,"w":522,"b":527,"h":0,"r":"?","dd":0},{"id":1002,"w":513,"b":521,"h":0,"r":"?","dd":0}]"""
        val pairings_R5 = """[{"id":1003,"w":528,"b":530,"h":0,"r":"?","dd":0},{"id":1004,"w":512,"b":517,"h":0,"r":"?","dd":0},{"id":1005,"w":504,"b":519,"h":0,"r":"?","dd":0},{"id":1006,"w":514,"b":509,"h":0,"r":"?","dd":0},{"id":1007,"w":506,"b":502,"h":0,"r":"?","dd":0},{"id":1008,"w":516,"b":518,"h":0,"r":"?","dd":0},{"id":1009,"w":511,"b":505,"h":0,"r":"?","dd":0},{"id":1010,"w":520,"b":526,"h":0,"r":"?","dd":0},{"id":1011,"w":525,"b":533,"h":0,"r":"?","dd":0},{"id":1012,"w":524,"b":508,"h":0,"r":"?","dd":0},{"id":1013,"w":503,"b":529,"h":0,"r":"?","dd":0},{"id":1014,"w":531,"b":532,"h":0,"r":"?","dd":0},{"id":1015,"w":527,"b":510,"h":0,"r":"?","dd":0},{"id":1016,"w":523,"b":515,"h":0,"r":"?","dd":0},{"id":1017,"w":507,"b":521,"h":0,"r":"?","dd":0},{"id":1018,"w":513,"b":522,"h":0,"r":"?","dd":0}]"""
        val pairings_R6 = """[{"id":1019,"w":519,"b":517,"h":0,"r":"?","dd":0},{"id":1020,"w":530,"b":509,"h":0,"r":"?","dd":0},{"id":1021,"w":502,"b":528,"h":0,"r":"?","dd":0},{"id":1022,"w":526,"b":504,"h":0,"r":"?","dd":0},{"id":1023,"w":505,"b":514,"h":0,"r":"?","dd":0},{"id":1024,"w":508,"b":506,"h":0,"r":"?","dd":0},{"id":1025,"w":533,"b":518,"h":0,"r":"?","dd":0},{"id":1026,"w":529,"b":512,"h":0,"r":"?","dd":0},{"id":1027,"w":524,"b":511,"h":0,"r":"?","dd":0},{"id":1028,"w":503,"b":520,"h":0,"r":"?","dd":0},{"id":1029,"w":532,"b":525,"h":0,"r":"?","dd":0},{"id":1030,"w":516,"b":515,"h":0,"r":"?","dd":0},{"id":1031,"w":521,"b":510,"h":0,"r":"?","dd":0},{"id":1032,"w":531,"b":527,"h":0,"r":"?","dd":0},{"id":1033,"w":507,"b":522,"h":0,"r":"?","dd":0},{"id":1034,"w":523,"b":513,"h":0,"r":"?","dd":0}]"""
        val pairings_R7 = """[{"id":1035,"w":528,"b":517,"h":0,"r":"?","dd":0},{"id":1036,"w":509,"b":504,"h":0,"r":"?","dd":0},{"id":1037,"w":530,"b":518,"h":0,"r":"?","dd":0},{"id":1038,"w":506,"b":519,"h":0,"r":"?","dd":0},{"id":1039,"w":514,"b":502,"h":0,"r":"?","dd":0},{"id":1040,"w":512,"b":510,"h":0,"r":"?","dd":0},{"id":1041,"w":533,"b":505,"h":0,"r":"?","dd":0},{"id":1042,"w":525,"b":511,"h":0,"r":"?","dd":0},{"id":1043,"w":529,"b":520,"h":0,"r":"?","dd":0},{"id":1044,"w":526,"b":515,"h":0,"r":"?","dd":0},{"id":1045,"w":508,"b":521,"h":0,"r":"?","dd":0},{"id":1046,"w":516,"b":527,"h":0,"r":"?","dd":0},{"id":1047,"w":522,"b":524,"h":0,"r":"?","dd":0},{"id":1048,"w":532,"b":503,"h":0,"r":"?","dd":0},{"id":1049,"w":531,"b":513,"h":0,"r":"?","dd":0},{"id":1050,"w":523,"b":507,"h":0,"r":"?","dd":0}]"""

        // read tournament without pairings
        var file_np = getTestFile("opengotha/tournamentfiles/simpleswiss_nopairings.xml")
        logger.info("read from file $file_np")
        val resource_np = file_np.readText(StandardCharsets.UTF_8)
        var resp_np = TestAPI.post("/api/tour", resource_np)
        val id_np = resp_np.asObject().getInt("id")
        assertNotNull(id_np)
        val tournament_np = TestAPI.get("/api/tour/$id_np").asObject()
        logger.info(tournament_np.toString().slice(0..50) + "...")
        val players_np = TestAPI.get("/api/tour/$id_np/part").asArray()
        logger.info(players_np.toString().slice(0..50) + "...")


        // *** Test Round 1 ***
        var games_np = TestAPI.post("/api/tour/$id_np/pair/1", Json.Array("all")).asArray()
        logger.info("games for round 1: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "weights.txt"), "Weights not equal to itself")
        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weightsonly_R1.txt"), "Not matching opengotha weights for round 1")
        assertTrue(compare_games(games_np, Json.parse(pairings_R1.toString())!!.asArray()), "pairings for round 1 differ")
        //assertEquals(pairings_R1, games_np.toString(), "pairings for round 1 differ")
        logger.info("Pairings for round 1 match OpenGotha")

        var firstGameID:Int = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/1", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 1")

        // *** Test Round 2 ***
        games_np = TestAPI.post("/api/tour/$id_np/pair/2", Json.Array("all")).asArray()
        logger.info("games for round 2: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R2.txt"), "Not matching opengotha weights for round 2")
        assertTrue(compare_games(games_np, Json.parse(pairings_R2.toString())!!.asArray()), "pairings for round 2 differ")
        //assertEquals(pairings_R2, games_np.toString(), "pairings for round 2 differ")
        logger.info("Pairings for round 2 match OpenGotha")


        firstGameID = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/2", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 2")

        // *** Test Round 3 ***
        games_np = TestAPI.post("/api/tour/$id_np/pair/3", Json.Array("all")).asArray()
        logger.info("games for round 3: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R3.txt"), "Not matching opengotha weights for round 3")
        assertTrue(compare_games(games_np, Json.parse(pairings_R3.toString())!!.asArray()), "pairings for round 3 differ")
        //assertEquals(pairings_R3, games_np.toString(), "pairings for round 3 differ")
        logger.info("Pairings for round 3 match OpenGotha")

        firstGameID = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/3", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 3")

        // *** Test Round 4 ***
        games_np = TestAPI.post("/api/tour/$id_np/pair/4", Json.Array("all")).asArray()
        logger.info("games for round 4: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R4.txt"), "Not matching opengotha weights for round 4")
        assertTrue(compare_games(games_np, Json.parse(pairings_R4.toString())!!.asArray()), "pairings for round 4 differ")
        //assertEquals(pairings_R4, games_np.toString(), "pairings for round 3 differ")
        logger.info("Pairings for round 4 match OpenGotha")

        firstGameID = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/4", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 4")

        // *** Test Round 5 ***
        games_np = TestAPI.post("/api/tour/$id_np/pair/5", Json.Array("all")).asArray()
        logger.info("games for round 5: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R5.txt"), "Not matching opengotha weights for round 5")
        assertTrue(compare_games(games_np, Json.parse(pairings_R5.toString())!!.asArray()), "pairings for round 5 differ")
        //assertEquals(pairings_R4, games_np.toString(), "pairings for round 3 differ")
        logger.info("Pairings for round 5 match OpenGotha")

        firstGameID = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/5", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 5")

        // *** Test Round 6 ***
        games_np = TestAPI.post("/api/tour/$id_np/pair/6", Json.Array("all")).asArray()
        logger.info("games for round 6: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R6.txt"), "Not matching opengotha weights for round 6")
        assertTrue(compare_games(games_np, Json.parse(pairings_R6.toString())!!.asArray()), "pairings for round 6 differ")
        logger.info("Pairings for round 6 match OpenGotha")

        firstGameID = (games_np.getJson(0)!!.asObject()["id"] as Long?)!!.toInt()
        for (game_id in firstGameID..firstGameID+15) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/6", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 6")

    }

}