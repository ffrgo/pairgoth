package org.jeudego.pairgoth.ext

import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.store.nextGameId
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.store.nextTournamentId
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.time.LocalDate

/**
 * MacMahon 3.9 format import support
 * Ported from OpenGothaCustom (https://bitbucket.org/kamyszyn/opengothacustom)
 */
object MacMahon39 {

    /**
     * Check if the XML element is in MacMahon 3.9 format
     */
    fun isFormat(element: Element): Boolean {
        val tournament = element.getElementsByTagName("Tournament").item(0) ?: return false
        val typeVersion = tournament.attributes?.getNamedItem("typeversion")?.nodeValue
        return typeVersion != null
    }

    /**
     * Import a MacMahon 3.9 format tournament
     */
    fun import(element: Element): Tournament<*> {
        val tournamentEl = element.getElementsByTagName("Tournament").item(0) as? Element
            ?: throw Error("No Tournament element found")

        // Parse tournament settings
        val name = extractValue("Name", tournamentEl, "Tournament")
        val numberOfRounds = extractValue("NumberOfRounds", tournamentEl, "5").toInt()
        val mmBarStr = extractValue("UpperMacMahonBarLevel", tournamentEl, "1d")
        val mmFloorStr = extractValue("LowerMacMahonBarLevel", tournamentEl, "30k")
        val isMMBar = extractValue("UpperMacMahonBar", tournamentEl, "true") == "true"
        val isMMFloor = extractValue("LowerMacMahonBar", tournamentEl, "true") == "true"
        val handicapUsed = extractValue("HandicapUsed", tournamentEl, "false").equals("true", ignoreCase = true)
        val handicapByRank = extractValue("HandicapByLevel", tournamentEl, "false").equals("true", ignoreCase = true)
        val handicapBelowStr = extractValue("HandicapBelowLevel", tournamentEl, "30k")
        val isHandicapBelow = extractValue("HandicapBelow", tournamentEl, "true").equals("true", ignoreCase = true)
        val handicapCorrectionStr = extractValue("HandicapAdjustmentValue", tournamentEl, "0")
        val isHandicapReduction = extractValue("HandicapAdjustment", tournamentEl, "true").equals("true", ignoreCase = true)
        val handicapCeilingStr = extractValue("HandicapLimitValue", tournamentEl, "9")
        val isHandicapLimit = extractValue("HandicapLimit", tournamentEl, "true").equals("true", ignoreCase = true)

        // Parse placement criteria from Walllist
        val walllistEl = element.getElementsByTagName("Walllist").item(0) as? Element
        val breakers = if (walllistEl != null) extractValues("ShortName", walllistEl) else listOf("Score", "SOS", "SOSOS")

        // Determine effective values
        val mmBar = if (isMMBar) parseRank(mmBarStr) else 8  // 9d
        val mmFloor = if (isMMFloor) parseRank(mmFloorStr) else -30 // 30k
        val handicapBelow = if (isHandicapBelow) parseRank(handicapBelowStr) else 8 // 9d
        val handicapCorrection = if (isHandicapReduction) -1 * handicapCorrectionStr.toInt() else 0
        val handicapCeiling = when {
            !handicapUsed -> 0
            !isHandicapLimit -> 30
            else -> handicapCeilingStr.toInt()
        }

        // Create pairing parameters
        val pairingParams = PairingParams(
            base = BaseCritParams(),
            main = MainCritParams(),
            secondary = SecondaryCritParams(),
            geo = GeographicalParams(),
            handicap = HandicapParams(
                useMMS = !handicapByRank,
                rankThreshold = handicapBelow,
                correction = handicapCorrection,
                ceiling = handicapCeiling
            )
        )

        // Create placement parameters from breakers
        val placementCrit = breakers.take(6).mapNotNull { translateBreaker(it, breakers.firstOrNull() == "Points") }.toTypedArray()
        val placementParams = PlacementParams(crit = if (placementCrit.isEmpty()) arrayOf(Criterion.MMS, Criterion.SOSM, Criterion.SOSOSM) else placementCrit)

        // Create tournament
        val tournament = StandardTournament(
            id = nextTournamentId,
            type = Tournament.Type.INDIVIDUAL,
            name = name,
            shortName = name.take(20),
            startDate = LocalDate.now(),
            endDate = LocalDate.now(),
            director = "",
            country = "",
            location = "",
            online = false,
            timeSystem = SuddenDeath(3600), // Default: 1 hour sudden death
            pairing = MacMahon(
                pairingParams = pairingParams,
                placementParams = placementParams,
                mmFloor = mmFloor,
                mmBar = mmBar
            ),
            rounds = numberOfRounds
        )

        // Parse players
        val playerIdMap = mutableMapOf<String, ID>()
        val goPlayers = element.getElementsByTagName("GoPlayer")
        for (i in 0 until goPlayers.length) {
            val playerEl = goPlayers.item(i) as? Element ?: continue
            val parentEl = playerEl.parentNode as? Element ?: continue

            val mm39Id = extractValue("Id", parentEl, "1")
            val egfPin = extractValue("EgdPin", playerEl, "").let { if (it.length < 8) "" else it }
            val firstname = extractValue("FirstName", playerEl, " ")
            val surname = extractValue("Surname", playerEl, " ")
            val club = extractValue("Club", playerEl, "")
            val country = extractValue("Country", playerEl, "").uppercase()
            val rankStr = extractValue("GoLevel", playerEl, "30k")
            val rank = parseRank(rankStr)
            val ratingStr = extractValue("Rating", playerEl, "-901")
            val rating = ratingStr.toInt()
            val superBarMember = extractValue("SuperBarMember", parentEl, "false") == "true"
            val preliminary = extractValue("PreliminaryRegistration", parentEl, "false") == "true"

            val player = Player(
                id = nextPlayerId,
                name = surname,
                firstname = firstname,
                rating = rating,
                rank = rank,
                country = if (country == "GB") "UK" else country,
                club = club,
                final = !preliminary,
                mmsCorrection = if (superBarMember) 1 else 0
            ).also {
                if (egfPin.isNotEmpty()) {
                    it.externalIds[DatabaseId.EGF] = egfPin
                }
                // Parse not playing rounds
                val notPlayingRounds = extractValues("NotPlayingInRound", parentEl)
                for (roundStr in notPlayingRounds) {
                    val round = roundStr.toIntOrNull() ?: continue
                    it.skip.add(round)
                }
            }

            playerIdMap[mm39Id] = player.id
            tournament.players[player.id] = player
        }

        // Parse games (pairings)
        val pairings = element.getElementsByTagName("Pairing")
        for (i in 0 until pairings.length) {
            val pairingEl = pairings.item(i) as? Element ?: continue
            val parentEl = pairingEl.parentNode as? Element ?: continue

            val isByeGame = extractValue("PairingWithBye", pairingEl, "false").equals("true", ignoreCase = true)
            val roundNumber = extractValue("RoundNumber", parentEl, "1").toInt()
            val boardNumber = extractValue("BoardNumber", pairingEl, "${i + 1}").toInt()

            if (isByeGame) {
                // Bye player
                val blackId = extractValue("Black", pairingEl, "")
                val playerId = playerIdMap[blackId] ?: continue
                val game = Game(
                    id = nextGameId,
                    table = 0,
                    white = playerId,
                    black = 0,
                    result = Game.Result.WHITE
                )
                tournament.games(roundNumber)[game.id] = game
            } else {
                // Regular game
                val whiteId = extractValue("White", pairingEl, "")
                val blackId = extractValue("Black", pairingEl, "")
                val whitePId = playerIdMap[whiteId] ?: continue
                val blackPId = playerIdMap[blackId] ?: continue
                val handicap = extractValue("Handicap", pairingEl, "0").toInt()
                val resultStr = extractValue("Result", pairingEl, "?-?")
                val resultByRef = extractValue("ResultByReferee", pairingEl, "false").equals("true", ignoreCase = true)

                val game = Game(
                    id = nextGameId,
                    table = boardNumber,
                    white = whitePId,
                    black = blackPId,
                    handicap = handicap,
                    result = parseResult(resultStr, resultByRef)
                )
                tournament.games(roundNumber)[game.id] = game
            }
        }

        return tournament
    }

    // Helper functions

    private fun extractValue(tag: String, element: Element, default: String): String {
        return try {
            val nodes = element.getElementsByTagName(tag).item(0)?.childNodes
            nodes?.item(0)?.nodeValue ?: default
        } catch (e: Exception) {
            default
        }
    }

    private fun extractValues(tag: String, element: Element): List<String> {
        val result = mutableListOf<String>()
        try {
            val nodeList = element.getElementsByTagName(tag)
            for (i in 0 until minOf(nodeList.length, 20)) {
                val nodes = nodeList.item(i)?.childNodes
                nodes?.item(0)?.nodeValue?.let { result.add(it) }
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    private fun parseRank(rankStr: String): Int {
        val regex = Regex("(\\d+)([kKdD])")
        val match = regex.matchEntire(rankStr) ?: return -20
        val (num, letter) = match.destructured
        val level = num.toIntOrNull() ?: return -20
        return when (letter.lowercase()) {
            "k" -> -level
            "d" -> level - 1
            else -> -20
        }
    }

    private fun parseResult(resultStr: String, byRef: Boolean): Game.Result {
        // MM39 result format: "1-0" (white wins), "0-1" (black wins), etc.
        // The format uses black-first convention (first number is black's score)
        return when (resultStr.removeSuffix("!")) {
            "0-1" -> Game.Result.WHITE
            "1-0" -> Game.Result.BLACK
            "\u00BD-\u00BD" -> Game.Result.JIGO
            "0-0" -> Game.Result.BOTHLOOSE
            "1-1" -> Game.Result.BOTHWIN
            else -> Game.Result.UNKNOWN
        }
    }

    private fun translateBreaker(breaker: String, swiss: Boolean): Criterion? {
        return when (breaker) {
            "Points" -> Criterion.NBW
            "Score", "ScoreX" -> Criterion.MMS
            "SOS" -> if (swiss) Criterion.SOSW else Criterion.SOSM
            "SOSOS" -> if (swiss) Criterion.SOSOSW else Criterion.SOSOSM
            "SODOS" -> if (swiss) Criterion.SODOSW else Criterion.SODOSM
            else -> null
        }
    }
}
