package org.jeudego.pairgoth.view

import com.republicate.kson.Json
import org.jeudego.pairgoth.ratings.RatingsManager
import org.jeudego.pairgoth.web.WebappManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk


/**
 * Generic utilities
 */

class PairgothTool {
    fun toMap(array: Json.Array) = array.map { ser -> ser as Json.Object }.associateBy { it.getLong("id")!! }

    fun getTeamMap(array: Json.Array) = array.flatMap { ser -> (ser as Json.Object).getArray("players")!!.map { Pair(it, ser.getLong("id")!!) } }.toMap()

    fun truncate(disp: String?, length: Int): String {
        if (disp == null) return ""
        if (disp.length <= length) return disp
        return disp.substring(0, length) + "…"
    }

    fun countFinals(array: Json.Array) = array.map { ser -> ser as Json.Object }.count { it.getBoolean("final") ?: false }

    fun getCriteria() = mapOf(
        "NONE" to "No tie break", // No ranking / tie-break

        // TODO "CATEGORY" to "Category",
        "RANK" to "Rank",
        "RATING" to "Rating",
        "NBW" to "Number of wins", // Number win
        "MMS" to "Mac Mahon score", // Macmahon score
        "SCOREX" to "Score X", // Score X
        // TODO "STS" to "Strasbourg score", // Strasbourg score
        // TODO "CPS" to "Cup score", // Cup score

        "SOSW" to "Sum of opponents wins", // Sum of opponents NBW
        "SOSWM1" to "Sum of opponents wins minus 1", //-1
        "SOSWM2" to "Sum of opponents wins minus 2", //-2
        "SODOSW" to "Sum of defeated opponents wins", // Sum of defeated opponents NBW
        "SOSOSW" to "Sum of opponents SOSW", // Sum of opponent SOS
        "CUSSW" to "Cumulative sum of opponents wins", // Cumulative sum of scores (NBW)

        "SOSM" to "Sum of opponents Mac Mahon score", // Sum of opponents McMahon score
        "SOSMM1" to "Sum of opponents Mac Mahon score minus 1", // Same as previous group but with McMahon score
        "SOSMM2" to "Sum of opponents Mac Mahon score minus 2",
        "SODOSM" to "Sum of defeated opponents Mac Mahon score",
        "SOSOSM" to "Sum of opponents SOSM",
        // TODO "CUSSM" to "Cumulative sum of opponents Mac Mahon score",

        // TODO "SOSTS" to "Sum of opponents Strasbourg score", // Sum of opponnents Strasbourg score

        // TODO "EXT" to "Attempted achievements", // Exploits tentes
        // TODO "EXR" to "Succeeded achievements", // Exploits reussis

        // For the two criteria below see the user documentation
        // TODO "SDC" to "Simplified direct confrontation", // Simplified direct confrontation
        // TODO "DC" to "Direct confrontation", // Direct confrontation
    )

    fun getResultsStats(games: Collection<Json.Object>): Json.Object {
        var total = 0
        var known = 0
        games
            .filter{ it.getInt("b")!! != 0 && it.getInt("w")!! != 0 }
            .map { it -> it.getString("r") }
            .forEach {
                ++total
                if ("?" != it) ++known
            }
        return Json.Object("total" to total, "known" to known)
    }

    fun getMmsMap(pairables: Collection<Json.Object>) =
        pairables.groupBy { pairable -> pairable.getDouble("MMS")?.toLong() }
        .mapValues { entry ->
            entry.value.sortedByDescending { pairable ->
                pairable.getInt("rating")
            }
        }

    fun getMmsPlayersMap(pairables: Collection<Json.Object>) =
        pairables.associate { part ->
            Pair(part.getLong("id"), part.getDouble("MMS")?.toLong())
        }

    fun removeBye(games: Collection<Json.Object>) =
        games.filter {
            it.getInt("b")!! != 0 && it.getInt("w")!! != 0
        }

    @OptIn(ExperimentalPathApi::class)
    fun getExampleTournaments(): List<String> {
        val classLoader: ClassLoader = PairgothTool::class.java.classLoader
        val examplesPath = Paths.get(classLoader.getResource(EXAMPLES_DIRECTORY).toURI())
        return examplesPath.walk().filter(Files::isRegularFile).map { it.fileName.toString().removeSuffix(".tour") }.sorted().toList()
    }

    fun getExampleTournament(name: String): Json.Object {
        val classLoader: ClassLoader = PairgothTool::class.java.classLoader
        return Json.parse(classLoader.getResource("$EXAMPLES_DIRECTORY/$name.tour").readText())?.asObject()
            ?: throw Error("wrong resource file")
    }

    companion object {
        const val EXAMPLES_DIRECTORY = "examples"
    }

    fun getRatingsDates() = RatingsManager.getRatingsDates()

    fun getTeamables(players: Collection<Json.Object>, teams: Collection<Json.Object>): List<Json.Object> {
        val teamed = teams.flatMap { team ->
            team.getArray("players")!!.map { it -> it as Long }
        }.toSet()
        return players.filter { p -> !teamed.contains(p.getLong("id")) }
    }

    // EGF ratings
    fun displayRatings(ratings: String, country: String): Boolean = WebappManager.properties.getProperty("ratings.${ratings}.enable")?.toBoolean() ?: (ratings.lowercase() != "ffg") || country.lowercase() == "fr"
    fun showRatings(ratings: String, country: String): Boolean = WebappManager.properties.getProperty("ratings.${ratings}.enable")?.toBoolean() ?: (ratings.lowercase() != "ffg") || country.lowercase() == "fr"
}