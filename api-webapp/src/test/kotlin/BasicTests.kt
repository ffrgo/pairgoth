package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.model.ID
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

    fun compare_games(games:String, pairings:String): Boolean{
        println("Compare games ")
        return false
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
        var file = getTestFile("opengotha/tournamentfiles/simpleswiss_orderTableNumberScoreRating.xml")
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
        var games_np = TestAPI.post("/api/tour/$id_np/pair/1", Json.Array("all")).asArray()
        logger.info("games for round 1: {}", games_np.toString())

        // logger.info("Compare weights with itself")
        assertTrue(compare_weights("weights.txt", "weights.txt"), "Weights not equal to itselft")
        // logger.info("Compare weights with opengotha")
        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weightsonly_R1.txt"), "Not matching opengotha weights for round 1")

        val pairings_R1 = """[{"id":843,"w":525,"b":530,"h":0,"r":"?","dd":0},{"id":844,"w":516,"b":514,"h":0,"r":"?","dd":0},{"id":845,"w":532,"b":524,"h":0,"r":"?","dd":0},{"id":846,"w":513,"b":509,"h":0,"r":"?","dd":0},{"id":847,"w":533,"b":508,"h":0,"r":"?","dd":0},{"id":848,"w":504,"b":517,"h":0,"r":"?","dd":0},{"id":849,"w":507,"b":506,"h":0,"r":"?","dd":0},{"id":850,"w":523,"b":529,"h":0,"r":"?","dd":0},{"id":851,"w":503,"b":518,"h":0,"r":"?","dd":0},{"id":852,"w":512,"b":528,"h":0,"r":"?","dd":0},{"id":853,"w":515,"b":510,"h":0,"r":"?","dd":0},{"id":854,"w":502,"b":531,"h":0,"r":"?","dd":0},{"id":855,"w":505,"b":519,"h":0,"r":"?","dd":0},{"id":856,"w":522,"b":511,"h":0,"r":"?","dd":0},{"id":857,"w":521,"b":526,"h":0,"r":"?","dd":0},{"id":858,"w":527,"b":520,"h":0,"r":"?","dd":0}]"""
        val pairings_R2 = """[{"id":859,"w":526,"b":530,"h":0,"r":"?","dd":0},{"id":860,"w":524,"b":514,"h":0,"r":"?","dd":0},{"id":861,"w":509,"b":517,"h":0,"r":"?","dd":0},{"id":862,"w":508,"b":518,"h":0,"r":"?","dd":0},{"id":863,"w":510,"b":506,"h":0,"r":"?","dd":0},{"id":864,"w":531,"b":529,"h":0,"r":"?","dd":0},{"id":865,"w":511,"b":528,"h":0,"r":"?","dd":0},{"id":866,"w":520,"b":519,"h":0,"r":"?","dd":0},{"id":867,"w":532,"b":516,"h":0,"r":"?","dd":0},{"id":868,"w":513,"b":504,"h":0,"r":"?","dd":0},{"id":869,"w":503,"b":533,"h":0,"r":"?","dd":0},{"id":870,"w":515,"b":507,"h":0,"r":"?","dd":0},{"id":871,"w":523,"b":502,"h":0,"r":"?","dd":0},{"id":872,"w":522,"b":512,"h":0,"r":"?","dd":0},{"id":873,"w":527,"b":505,"h":0,"r":"?","dd":0},{"id":874,"w":521,"b":525,"h":0,"r":"?","dd":0}]"""
        val pairings_R3 = """[{"id":875,"w":519,"b":530,"h":0,"r":"?","dd":0},{"id":876,"w":514,"b":517,"h":0,"r":"?","dd":0},{"id":877,"w":529,"b":506,"h":0,"r":"?","dd":0},{"id":878,"w":518,"b":528,"h":0,"r":"?","dd":0},{"id":879,"w":507,"b":508,"h":0,"r":"?","dd":0},{"id":880,"w":531,"b":512,"h":0,"r":"?","dd":0},{"id":881,"w":510,"b":509,"h":0,"r":"?","dd":0},{"id":882,"w":505,"b":504,"h":0,"r":"?","dd":0},{"id":883,"w":526,"b":511,"h":0,"r":"?","dd":0},{"id":884,"w":525,"b":520,"h":0,"r":"?","dd":0},{"id":885,"w":516,"b":502,"h":0,"r":"?","dd":0},{"id":886,"w":524,"b":533,"h":0,"r":"?","dd":0},{"id":887,"w":522,"b":503,"h":0,"r":"?","dd":0},{"id":888,"w":527,"b":523,"h":0,"r":"?","dd":0},{"id":889,"w":513,"b":532,"h":0,"r":"?","dd":0},{"id":890,"w":521,"b":515,"h":0,"r":"?","dd":0}]"""
        val pairings_R4 = """[{"id":891,"w":506,"b":528,"h":0,"r":"?","dd":0},{"id":892,"w":517,"b":530,"h":0,"r":"?","dd":0},{"id":893,"w":518,"b":512,"h":0,"r":"?","dd":0},{"id":894,"w":511,"b":519,"h":0,"r":"?","dd":0},{"id":895,"w":508,"b":504,"h":0,"r":"?","dd":0},{"id":896,"w":533,"b":514,"h":0,"r":"?","dd":0},{"id":897,"w":529,"b":502,"h":0,"r":"?","dd":0},{"id":898,"w":520,"b":509,"h":0,"r":"?","dd":0},{"id":899,"w":531,"b":516,"h":0,"r":"?","dd":0},{"id":900,"w":507,"b":503,"h":0,"r":"?","dd":0},{"id":901,"w":510,"b":505,"h":0,"r":"?","dd":0},{"id":902,"w":523,"b":524,"h":0,"r":"?","dd":0},{"id":903,"w":532,"b":526,"h":0,"r":"?","dd":0},{"id":904,"w":515,"b":525,"h":0,"r":"?","dd":0},{"id":905,"w":522,"b":527,"h":0,"r":"?","dd":0},{"id":906,"w":513,"b":521,"h":0,"r":"?","dd":0}]"""
        val pairings_R5 = """[{"id":907,"w":528,"b":530,"h":0,"r":"?","dd":0},{"id":908,"w":512,"b":517,"h":0,"r":"?","dd":0},{"id":909,"w":504,"b":519,"h":0,"r":"?","dd":0},{"id":910,"w":514,"b":509,"h":0,"r":"?","dd":0},{"id":911,"w":506,"b":502,"h":0,"r":"?","dd":0},{"id":912,"w":516,"b":518,"h":0,"r":"?","dd":0},{"id":913,"w":511,"b":505,"h":0,"r":"?","dd":0},{"id":914,"w":520,"b":526,"h":0,"r":"?","dd":0},{"id":915,"w":525,"b":533,"h":0,"r":"?","dd":0},{"id":916,"w":524,"b":508,"h":0,"r":"?","dd":0},{"id":917,"w":503,"b":529,"h":0,"r":"?","dd":0},{"id":918,"w":531,"b":532,"h":0,"r":"?","dd":0},{"id":919,"w":527,"b":510,"h":0,"r":"?","dd":0},{"id":920,"w":523,"b":515,"h":0,"r":"?","dd":0},{"id":921,"w":507,"b":521,"h":0,"r":"?","dd":0},{"id":922,"w":513,"b":522,"h":0,"r":"?","dd":0}]"""


        //logger.info(compare_string(pairings_R1, games_np.toString()))
        // val games = TestAPI.get("/api/tour/$id/res/1").asArray()
        //logger.info("Compare pairings for round 1")
        assertEquals(pairings_R1, games_np.toString(), "pairings for round 1 differ")
        logger.info("Pairings for round 1 match OpenGotha")

        //val results_R1 = ["""{"id":843,"result":"b"}""",  """{"id":844,"result":"b"}"""]//,{"id":846,"w":513,"b":509,"h":0,"r":"?","dd":0},{"id":847,"w":533,"b":508,"h":0,"r":"?","dd":0},{"id":848,"w":504,"b":517,"h":0,"r":"?","dd":0},{"id":849,"w":507,"b":506,"h":0,"r":"?","dd":0},{"id":850,"w":523,"b":529,"h":0,"r":"?","dd":0},{"id":851,"w":503,"b":518,"h":0,"r":"?","dd":0},{"id":852,"w":512,"b":528,"h":0,"r":"?","dd":0},{"id":853,"w":515,"b":510,"h":0,"r":"?","dd":0},{"id":854,"w":502,"b":531,"h":0,"r":"?","dd":0},{"id":855,"w":505,"b":519,"h":0,"r":"?","dd":0},{"id":856,"w":522,"b":511,"h":0,"r":"?","dd":0},{"id":857,"w":521,"b":526,"h":0,"r":"?","dd":0},{"id":858,"w":527,"b":520,"h":0,"r":"?","dd":0}]"""
        for (game_id in 843..858) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/1", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 1")

        games_np = TestAPI.post("/api/tour/$id_np/pair/2", Json.Array("all")).asArray()
        logger.info("games for round 2: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R2.txt"), "Not matching opengotha weights for round 2")
        assertEquals(pairings_R2, games_np.toString(), "pairings for round 2 differ")
        logger.info("Pairings for round 2 match OpenGotha")

        for (game_id in 859..874) {
            resp_np = TestAPI.put("/api/tour/$id_np/res/2", Json.parse("""{"id":$game_id,"result":"b"}""")).asObject()
            assertTrue(resp_np.getBoolean("success") == true, "expecting success")
        }
        logger.info("Results succesfully entered for round 2")

        games_np = TestAPI.post("/api/tour/$id_np/pair/3", Json.Array("all")).asArray()
        logger.info("games for round 3: {}", games_np.toString())

        assertTrue(compare_weights("weights.txt", "opengotha/simpleswiss_weights_R3.txt"), "Not matching opengotha weights for round 3")
        assertEquals(pairings_R3, games_np.toString(), "pairings for round 3 differ")
        logger.info("Pairings for round 3 match OpenGotha")




    }

}