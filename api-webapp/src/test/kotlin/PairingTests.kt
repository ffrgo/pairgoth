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
import kotlin.reflect.typeOf
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
        if (games.size != opengotha.size) {
            val tmp = Game.fromJson(games.getJson(games.size-1)!!.asObject())
            if ((tmp.white != 0) and (tmp.black != 0)) {return false}
        }
        val gamesPair = mutableSetOf<Pair<ID,ID>>()
        val openGothaPair = mutableSetOf<Pair<ID,ID>>()
        for (i in 0 until opengotha.size) {
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

        val pairingsR1 = """[{"id":601,"w":327,"b":332,"h":0,"r":"b","dd":0},{"id":602,"w":318,"b":316,"h":0,"r":"b","dd":0},{"id":603,"w":334,"b":326,"h":0,"r":"b","dd":0},{"id":604,"w":315,"b":311,"h":0,"r":"b","dd":0},{"id":605,"w":335,"b":310,"h":0,"r":"b","dd":0},{"id":606,"w":306,"b":319,"h":0,"r":"b","dd":0},{"id":607,"w":309,"b":308,"h":0,"r":"b","dd":0},{"id":608,"w":325,"b":331,"h":0,"r":"b","dd":0},{"id":609,"w":305,"b":320,"h":0,"r":"b","dd":0},{"id":610,"w":314,"b":330,"h":0,"r":"b","dd":0},{"id":611,"w":317,"b":312,"h":0,"r":"b","dd":0},{"id":612,"w":304,"b":333,"h":0,"r":"b","dd":0},{"id":613,"w":307,"b":321,"h":0,"r":"b","dd":0},{"id":614,"w":324,"b":313,"h":0,"r":"b","dd":0},{"id":615,"w":323,"b":328,"h":0,"r":"b","dd":0},{"id":616,"w":329,"b":322,"h":0,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":617,"w":328,"b":332,"h":0,"r":"b","dd":0},{"id":618,"w":326,"b":316,"h":0,"r":"b","dd":0},{"id":619,"w":311,"b":319,"h":0,"r":"b","dd":0},{"id":620,"w":310,"b":320,"h":0,"r":"b","dd":0},{"id":621,"w":312,"b":308,"h":0,"r":"b","dd":0},{"id":622,"w":333,"b":331,"h":0,"r":"b","dd":0},{"id":623,"w":313,"b":330,"h":0,"r":"b","dd":0},{"id":624,"w":322,"b":321,"h":0,"r":"b","dd":0},{"id":625,"w":334,"b":318,"h":0,"r":"b","dd":0},{"id":626,"w":315,"b":306,"h":0,"r":"b","dd":0},{"id":627,"w":305,"b":335,"h":0,"r":"b","dd":0},{"id":628,"w":317,"b":309,"h":0,"r":"b","dd":0},{"id":629,"w":325,"b":304,"h":0,"r":"b","dd":0},{"id":630,"w":324,"b":314,"h":0,"r":"b","dd":0},{"id":631,"w":329,"b":307,"h":0,"r":"b","dd":0},{"id":632,"w":323,"b":327,"h":0,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":633,"w":321,"b":332,"h":0,"r":"b","dd":0},{"id":634,"w":316,"b":319,"h":0,"r":"b","dd":0},{"id":635,"w":331,"b":308,"h":0,"r":"b","dd":0},{"id":636,"w":320,"b":330,"h":0,"r":"b","dd":0},{"id":637,"w":312,"b":311,"h":0,"r":"b","dd":0},{"id":638,"w":307,"b":306,"h":0,"r":"b","dd":0},{"id":639,"w":328,"b":313,"h":0,"r":"b","dd":0},{"id":640,"w":327,"b":322,"h":0,"r":"b","dd":0},{"id":641,"w":309,"b":310,"h":0,"r":"b","dd":0},{"id":642,"w":333,"b":314,"h":0,"r":"b","dd":0},{"id":643,"w":318,"b":304,"h":0,"r":"b","dd":0},{"id":644,"w":326,"b":335,"h":0,"r":"b","dd":0},{"id":645,"w":315,"b":334,"h":0,"r":"b","dd":0},{"id":646,"w":323,"b":317,"h":0,"r":"b","dd":0},{"id":647,"w":324,"b":305,"h":0,"r":"b","dd":0},{"id":648,"w":329,"b":325,"h":0,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":649,"w":308,"b":330,"h":0,"r":"b","dd":0},{"id":650,"w":319,"b":332,"h":0,"r":"b","dd":0},{"id":651,"w":320,"b":314,"h":0,"r":"b","dd":0},{"id":652,"w":313,"b":321,"h":0,"r":"b","dd":0},{"id":653,"w":310,"b":306,"h":0,"r":"b","dd":0},{"id":654,"w":335,"b":316,"h":0,"r":"b","dd":0},{"id":655,"w":331,"b":304,"h":0,"r":"b","dd":0},{"id":656,"w":322,"b":311,"h":0,"r":"b","dd":0},{"id":657,"w":333,"b":318,"h":0,"r":"b","dd":0},{"id":658,"w":309,"b":305,"h":0,"r":"b","dd":0},{"id":659,"w":312,"b":307,"h":0,"r":"b","dd":0},{"id":660,"w":325,"b":326,"h":0,"r":"b","dd":0},{"id":661,"w":334,"b":328,"h":0,"r":"b","dd":0},{"id":662,"w":317,"b":327,"h":0,"r":"b","dd":0},{"id":663,"w":324,"b":329,"h":0,"r":"b","dd":0},{"id":664,"w":315,"b":323,"h":0,"r":"b","dd":0}]"""
        val pairingsR5 = """[{"id":665,"w":330,"b":332,"h":0,"r":"b","dd":0},{"id":666,"w":314,"b":319,"h":0,"r":"b","dd":0},{"id":667,"w":306,"b":321,"h":0,"r":"b","dd":0},{"id":668,"w":316,"b":311,"h":0,"r":"b","dd":0},{"id":669,"w":308,"b":304,"h":0,"r":"b","dd":0},{"id":670,"w":318,"b":320,"h":0,"r":"b","dd":0},{"id":671,"w":313,"b":307,"h":0,"r":"b","dd":0},{"id":672,"w":322,"b":328,"h":0,"r":"b","dd":0},{"id":673,"w":327,"b":335,"h":0,"r":"b","dd":0},{"id":674,"w":326,"b":310,"h":0,"r":"b","dd":0},{"id":675,"w":305,"b":331,"h":0,"r":"b","dd":0},{"id":676,"w":333,"b":334,"h":0,"r":"b","dd":0},{"id":677,"w":329,"b":312,"h":0,"r":"b","dd":0},{"id":678,"w":325,"b":317,"h":0,"r":"b","dd":0},{"id":679,"w":309,"b":323,"h":0,"r":"b","dd":0},{"id":680,"w":315,"b":324,"h":0,"r":"b","dd":0}]"""
        val pairingsR6 = """[{"id":681,"w":321,"b":319,"h":0,"r":"b","dd":0},{"id":682,"w":332,"b":311,"h":0,"r":"b","dd":0},{"id":683,"w":304,"b":330,"h":0,"r":"b","dd":0},{"id":684,"w":328,"b":306,"h":0,"r":"b","dd":0},{"id":685,"w":307,"b":316,"h":0,"r":"b","dd":0},{"id":686,"w":310,"b":308,"h":0,"r":"b","dd":0},{"id":687,"w":335,"b":320,"h":0,"r":"b","dd":0},{"id":688,"w":331,"b":314,"h":0,"r":"b","dd":0},{"id":689,"w":326,"b":313,"h":0,"r":"b","dd":0},{"id":690,"w":305,"b":322,"h":0,"r":"b","dd":0},{"id":691,"w":334,"b":327,"h":0,"r":"b","dd":0},{"id":692,"w":318,"b":317,"h":0,"r":"b","dd":0},{"id":693,"w":323,"b":312,"h":0,"r":"b","dd":0},{"id":694,"w":333,"b":329,"h":0,"r":"b","dd":0},{"id":695,"w":309,"b":324,"h":0,"r":"b","dd":0},{"id":696,"w":325,"b":315,"h":0,"r":"b","dd":0}]"""
        /*
        2 solutions have the same sum of weights, OpenGotha pairing is
        val pairingsR7 = """[{"id":697,"w":330,"b":319,"h":0,"r":"b","dd":0},{"id":698,"w":311,"b":306,"h":0,"r":"b","dd":0},{"id":699,"w":332,"b":320,"h":0,"r":"b","dd":0},{"id":700,"w":308,"b":321,"h":0,"r":"b","dd":0},{"id":701,"w":316,"b":304,"h":0,"r":"b","dd":0},{"id":702,"w":314,"b":312,"h":0,"r":"b","dd":0},{"id":703,"w":335,"b":307,"h":0,"r":"b","dd":0},{"id":704,"w":327,"b":313,"h":0,"r":"b","dd":0},{"id":705,"w":331,"b":322,"h":0,"r":"b","dd":0},{"id":706,"w":328,"b":317,"h":0,"r":"b","dd":0},{"id":707,"w":310,"b":323,"h":0,"r":"b","dd":0},{"id":708,"w":318,"b":329,"h":0,"r":"b","dd":0},{"id":709,"w":324,"b":326,"h":0,"r":"b","dd":0},{"id":710,"w":334,"b":305,"h":0,"r":"b","dd":0},{"id":711,"w":333,"b":315,"h":0,"r":"b","dd":0},{"id":712,"w":325,"b":309,"h":0,"r":"b","dd":0}]"""
         */
        val pairingsR7 = """[{"id":697,"w":330,"b":319,"h":0,"r":"b","dd":0},{"id":698,"w":311,"b":314,"h":0,"r":"b","dd":0},{"id":699,"w":332,"b":320,"h":0,"r":"b","dd":0},{"id":700,"w":308,"b":321,"h":0,"r":"b","dd":0},{"id":701,"w":316,"b":304,"h":0,"r":"b","dd":0},{"id":702,"w":306,"b":312,"h":0,"r":"b","dd":0},{"id":703,"w":335,"b":307,"h":0,"r":"b","dd":0},{"id":704,"w":327,"b":313,"h":0,"r":"b","dd":0},{"id":705,"w":331,"b":322,"h":0,"r":"b","dd":0},{"id":706,"w":328,"b":317,"h":0,"r":"b","dd":0},{"id":707,"w":310,"b":323,"h":0,"r":"b","dd":0},{"id":708,"w":318,"b":329,"h":0,"r":"b","dd":0},{"id":709,"w":324,"b":326,"h":0,"r":"b","dd":0},{"id":710,"w":334,"b":305,"h":0,"r":"b","dd":0},{"id":711,"w":333,"b":315,"h":0,"r":"b","dd":0},{"id":712,"w":325,"b":309,"h":0,"r":"b","dd":0}]"""
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

        val pairingsR1 = """[{"id":713,"w":367,"b":339,"h":0,"r":"b","dd":0},{"id":714,"w":351,"b":366,"h":0,"r":"b","dd":0},{"id":715,"w":344,"b":348,"h":0,"r":"b","dd":0},{"id":716,"w":369,"b":357,"h":0,"r":"b","dd":0},{"id":717,"w":345,"b":343,"h":0,"r":"b","dd":0},{"id":718,"w":342,"b":349,"h":0,"r":"b","dd":0},{"id":719,"w":361,"b":338,"h":0,"r":"b","dd":0},{"id":720,"w":360,"b":340,"h":0,"r":"b","dd":0},{"id":721,"w":341,"b":337,"h":0,"r":"b","dd":0},{"id":722,"w":365,"b":347,"h":0,"r":"b","dd":0},{"id":723,"w":353,"b":350,"h":0,"r":"b","dd":0},{"id":724,"w":346,"b":336,"h":0,"r":"b","dd":0},{"id":725,"w":355,"b":352,"h":0,"r":"b","dd":0},{"id":726,"w":358,"b":370,"h":0,"r":"b","dd":0},{"id":727,"w":356,"b":363,"h":0,"r":"b","dd":0},{"id":728,"w":368,"b":362,"h":0,"r":"b","dd":0},{"id":729,"w":364,"b":359,"h":0,"r":"b","dd":0}]"""
        val pairingsR2 = """[{"id":730,"w":362,"b":348,"h":0,"r":"b","dd":0},{"id":731,"w":349,"b":343,"h":0,"r":"b","dd":0},{"id":732,"w":339,"b":338,"h":0,"r":"b","dd":0},{"id":733,"w":357,"b":340,"h":0,"r":"b","dd":0},{"id":734,"w":366,"b":370,"h":0,"r":"b","dd":0},{"id":735,"w":347,"b":337,"h":0,"r":"b","dd":0},{"id":736,"w":352,"b":336,"h":0,"r":"b","dd":0},{"id":737,"w":350,"b":354,"h":0,"r":"b","dd":0},{"id":738,"w":363,"b":367,"h":0,"r":"b","dd":0},{"id":739,"w":351,"b":360,"h":0,"r":"b","dd":0},{"id":740,"w":369,"b":346,"h":0,"r":"b","dd":0},{"id":741,"w":368,"b":342,"h":0,"r":"b","dd":0},{"id":742,"w":365,"b":353,"h":0,"r":"b","dd":0},{"id":743,"w":344,"b":355,"h":0,"r":"b","dd":0},{"id":744,"w":341,"b":358,"h":0,"r":"b","dd":0},{"id":745,"w":364,"b":356,"h":0,"r":"b","dd":0},{"id":746,"w":361,"b":345,"h":0,"r":"b","dd":0}]"""
        val pairingsR3 = """[{"id":747,"w":340,"b":348,"h":0,"r":"b","dd":0},{"id":748,"w":337,"b":336,"h":0,"r":"b","dd":0},{"id":749,"w":370,"b":354,"h":0,"r":"b","dd":0},{"id":750,"w":343,"b":359,"h":0,"r":"b","dd":0},{"id":751,"w":349,"b":358,"h":0,"r":"b","dd":0},{"id":752,"w":346,"b":367,"h":0,"r":"b","dd":0},{"id":753,"w":357,"b":345,"h":0,"r":"b","dd":0},{"id":754,"w":338,"b":363,"h":0,"r":"b","dd":0},{"id":755,"w":362,"b":360,"h":0,"r":"b","dd":0},{"id":756,"w":352,"b":347,"h":0,"r":"b","dd":0},{"id":757,"w":355,"b":350,"h":0,"r":"b","dd":0},{"id":758,"w":342,"b":339,"h":0,"r":"b","dd":0},{"id":759,"w":353,"b":366,"h":0,"r":"b","dd":0},{"id":760,"w":351,"b":361,"h":0,"r":"b","dd":0},{"id":761,"w":369,"b":344,"h":0,"r":"b","dd":0},{"id":762,"w":365,"b":364,"h":0,"r":"b","dd":0},{"id":763,"w":341,"b":368,"h":0,"r":"b","dd":0}]"""
        val pairingsR4 = """[{"id":985,"w":500,"b":522,"h":0,"r":"b","dd":0},{"id":986,"w":511,"b":524,"h":0,"r":"b","dd":0},{"id":987,"w":512,"b":506,"h":0,"r":"b","dd":0},{"id":988,"w":505,"b":513,"h":0,"r":"b","dd":0},{"id":989,"w":502,"b":498,"h":0,"r":"b","dd":0},{"id":990,"w":527,"b":508,"h":0,"r":"b","dd":0},{"id":991,"w":523,"b":496,"h":0,"r":"b","dd":0},{"id":992,"w":514,"b":503,"h":0,"r":"b","dd":0},{"id":993,"w":525,"b":510,"h":0,"r":"b","dd":0},{"id":994,"w":501,"b":497,"h":0,"r":"b","dd":0},{"id":995,"w":504,"b":499,"h":0,"r":"b","dd":0},{"id":996,"w":517,"b":518,"h":0,"r":"b","dd":0},{"id":997,"w":526,"b":520,"h":0,"r":"b","dd":0},{"id":998,"w":509,"b":519,"h":0,"r":"b","dd":0},{"id":999,"w":516,"b":521,"h":0,"r":"b","dd":0},{"id":1000,"w":507,"b":515,"h":0,"r":"b","dd":0}]"""
        val pairingsR5 = """[{"id":1001,"w":522,"b":524,"h":0,"r":"b","dd":0},{"id":1002,"w":506,"b":511,"h":0,"r":"b","dd":0},{"id":1003,"w":498,"b":513,"h":0,"r":"b","dd":0},{"id":1004,"w":508,"b":503,"h":0,"r":"b","dd":0},{"id":1005,"w":500,"b":496,"h":0,"r":"b","dd":0},{"id":1006,"w":510,"b":512,"h":0,"r":"b","dd":0},{"id":1007,"w":505,"b":499,"h":0,"r":"b","dd":0},{"id":1008,"w":514,"b":520,"h":0,"r":"b","dd":0},{"id":1009,"w":519,"b":527,"h":0,"r":"b","dd":0},{"id":1010,"w":518,"b":502,"h":0,"r":"b","dd":0},{"id":1011,"w":497,"b":523,"h":0,"r":"b","dd":0},{"id":1012,"w":525,"b":526,"h":0,"r":"b","dd":0},{"id":1013,"w":521,"b":504,"h":0,"r":"b","dd":0},{"id":1014,"w":517,"b":509,"h":0,"r":"b","dd":0},{"id":1015,"w":501,"b":515,"h":0,"r":"b","dd":0},{"id":1016,"w":507,"b":516,"h":0,"r":"b","dd":0}]"""
        val pairingsR6 = """[{"id":1017,"w":513,"b":511,"h":0,"r":"b","dd":0},{"id":1018,"w":524,"b":503,"h":0,"r":"b","dd":0},{"id":1019,"w":496,"b":522,"h":0,"r":"b","dd":0},{"id":1020,"w":520,"b":498,"h":0,"r":"b","dd":0},{"id":1021,"w":499,"b":508,"h":0,"r":"b","dd":0},{"id":1022,"w":502,"b":500,"h":0,"r":"b","dd":0},{"id":1023,"w":527,"b":512,"h":0,"r":"b","dd":0},{"id":1024,"w":523,"b":506,"h":0,"r":"b","dd":0},{"id":1025,"w":518,"b":505,"h":0,"r":"b","dd":0},{"id":1026,"w":497,"b":514,"h":0,"r":"b","dd":0},{"id":1027,"w":526,"b":519,"h":0,"r":"b","dd":0},{"id":1028,"w":510,"b":509,"h":0,"r":"b","dd":0},{"id":1029,"w":515,"b":504,"h":0,"r":"b","dd":0},{"id":1030,"w":525,"b":521,"h":0,"r":"b","dd":0},{"id":1031,"w":501,"b":516,"h":0,"r":"b","dd":0},{"id":1032,"w":517,"b":507,"h":0,"r":"b","dd":0}]"""
        val pairingsR7 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":506,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":498,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
        val pairingsR8 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":506,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":498,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
        val pairingsR9 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":506,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":498,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
        val pairingsR10 = """[{"id":1033,"w":522,"b":511,"h":0,"r":"b","dd":0},{"id":1034,"w":503,"b":506,"h":0,"r":"b","dd":0},{"id":1035,"w":524,"b":512,"h":0,"r":"b","dd":0},{"id":1036,"w":500,"b":513,"h":0,"r":"b","dd":0},{"id":1037,"w":508,"b":496,"h":0,"r":"b","dd":0},{"id":1038,"w":498,"b":504,"h":0,"r":"b","dd":0},{"id":1039,"w":527,"b":499,"h":0,"r":"b","dd":0},{"id":1040,"w":519,"b":505,"h":0,"r":"b","dd":0},{"id":1041,"w":523,"b":514,"h":0,"r":"b","dd":0},{"id":1042,"w":520,"b":509,"h":0,"r":"b","dd":0},{"id":1043,"w":502,"b":515,"h":0,"r":"b","dd":0},{"id":1044,"w":510,"b":521,"h":0,"r":"b","dd":0},{"id":1045,"w":516,"b":518,"h":0,"r":"b","dd":0},{"id":1046,"w":526,"b":497,"h":0,"r":"b","dd":0},{"id":1047,"w":525,"b":507,"h":0,"r":"b","dd":0},{"id":1048,"w":517,"b":501,"h":0,"r":"b","dd":0}]"""
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

        var games: Json.Array
        var firstGameID: Int
        var playersList = mutableListOf<Long>()
        
        var forcedPairingList = mutableListOf<Int>()
        var forcedPairing = mutableListOf<Json>()
        var forcedGames: Json.Array
        var game: Json

        for (i in 0..34){
            playersList.add(players.getJson(i)!!.asObject()["id"] as Long)
        }

        val byePlayerList = mutableListOf<Long>(354, 359, 356, 357, 345, 339, 368, 344, 349, 341)

        for (round in 1..7) {
            //games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array(playersList.filter{it != byePlayerList[round-1]})).asArray()

            if (round in forcedPairingList){
                // games must be created and then modified by PUT
                games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
                forcedPairing = mutableListOf<Json>()
                forcedGames = Json.parse(pairingsR1)!!.asArray()
                for (j in 0..forcedGames.size-1) {
                    game = forcedGames.getJson(j)!!.asObject()
                    TestAPI.put("/api/tour/$id/pair/$round", game)
                }
                games = TestAPI.get("/api/tour/$id/res/$round").asArray()
            }
            else {
                //games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array(playersList.filter{it != byePlayerList[round-1]})).asArray()
                games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
                logger.info("games for round $round: {}", games.toString())

                assertTrue(compare_weights("weights.txt", "opengotha/notsosimpleswiss_weights_R$round.txt"), "Not matching opengotha weights for round $round")
                assertTrue(compare_games(games, Json.parse(pairings[round - 1])!!.asArray()),"pairings for round $round differ")
                logger.info("Pairings for round $round match OpenGotha")
            }
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
            // games must be created and then modified by PUT
            games = TestAPI.post("/api/tour/$id/pair/$round", Json.Array("all")).asArray()
            assertTrue(compare_weights("weights.txt", "opengotha/simplemm/simplemm_weights_R$round.txt"), "Not matching opengotha weights for round $round")
            logger.info("Weights for round $round match OpenGotha")

            forcedGames = Json.parse(pairings[round-1])!!.asArray()
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