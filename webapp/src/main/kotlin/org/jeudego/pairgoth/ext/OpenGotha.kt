package org.jeudego.pairgoth.ext

import org.jeudego.pairgoth.model.CanadianByoyomi
import org.jeudego.pairgoth.model.FischerTime
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.StandardByoyomi
import org.jeudego.pairgoth.model.SuddenDeath
import org.jeudego.pairgoth.model.Swiss
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.parseRank
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.util.XmlFormat
import org.jeudego.pairgoth.util.booleanAttr
import org.jeudego.pairgoth.util.childrenArrayOf
import org.jeudego.pairgoth.util.dateAttr
import org.jeudego.pairgoth.util.doubleAttr
import org.jeudego.pairgoth.util.find
import org.jeudego.pairgoth.util.get
import org.jeudego.pairgoth.util.intAttr
import org.jeudego.pairgoth.util.objectOf
import org.jeudego.pairgoth.util.optBoolean
import org.jeudego.pairgoth.util.stringAttr
import org.w3c.dom.Element
import java.util.*

class OpenGothaFormat(xml: Element): XmlFormat(xml) {

    val Players by childrenArrayOf<Player>()
    val Games by childrenArrayOf<Game>()
    val TournamentParameterSet by objectOf<Params>()

    class Player(xml: Element): XmlFormat(xml) {
        val agaId by stringAttr()
        val club by stringAttr()
        val country by stringAttr()
        val egfPin by stringAttr()
        val ffgLicence by stringAttr()
        val firstName by stringAttr()
        val name by stringAttr()
        val participating by stringAttr()
        val rank by stringAttr()
        val rating by intAttr()
    }

    class Game(xml: Element): XmlFormat(xml) {
        val blackPlayer by stringAttr()
        val whitePlayer by stringAttr()
        val handicap by intAttr()
        val knownColor by booleanAttr()
        val result by stringAttr()
        val roundNumber by intAttr()
    }

    class Params(xml: Element): XmlFormat(xml) {
        val GeneralParameterSet by objectOf<GenParams>()
        val HandicapParameterSet by objectOf<HandicapParams>()
        val PairingParameterSet by objectOf<PairingParams>()

        class GenParams(xml: Element): XmlFormat(xml) {
            val bInternet by optBoolean()
            val basicTime by intAttr()
            val beginDate by dateAttr()
            val canByoYomiTime by intAttr()
            val complementaryTimeSystem by stringAttr()
            val endDate by dateAttr()
            val fisherTime by intAttr()
            val genCountNotPlayedGamesAsHalfPoint by booleanAttr()
            val genMMBar by stringAttr()
            val genMMFloor by stringAttr()
            val komi by doubleAttr()
            val location by stringAttr()
            val name by stringAttr()
            val nbMovesCanTime by intAttr()
            val numberOfCategories by intAttr()
            val numberOfRounds by intAttr()
            val shortName by stringAttr()
            val size by intAttr()
            val stdByoYomiTime by intAttr()
        }
        class HandicapParams(xml: Element): XmlFormat(xml) {
            val hdBasedOnMMS by booleanAttr()
            val hdCeiling by intAttr()
            val hdCorrection by intAttr()
            val hdNoHdRankThreshold by stringAttr()
        }
        class PairingParams(xml: Element): XmlFormat(xml) {
            val paiMaSeedSystem1 by stringAttr()
            val paiMaSeedSystem2 by stringAttr()
        }
    }
}

object OpenGotha {
    fun import(element: Element): Tournament {
        val imported = OpenGothaFormat(element)
        val genParams = imported.TournamentParameterSet.GeneralParameterSet
        val handParams = imported.TournamentParameterSet.HandicapParameterSet
        val pairingParams = imported.TournamentParameterSet.PairingParameterSet
        val tournament = Tournament(
            id = Store.nextTournamentId,
            type = Tournament.Type.INDIVIDUAL, // CB for now, TODO
            name = genParams.name,
            shortName = genParams.shortName,
            startDate = genParams.beginDate,
            endDate = genParams.endDate,
            country = "FR", // no country in opengotha format
            location = genParams.location,
            online = genParams.bInternet ?: false,
            timeSystem = when (genParams.complementaryTimeSystem) {
                "SUDDENDEATH" -> SuddenDeath(genParams.basicTime)
                "STDBYOYOMI" -> StandardByoyomi(genParams.basicTime, genParams.stdByoYomiTime, 1) // no periods?
                "CANBYOYOMI" -> CanadianByoyomi(genParams.basicTime, genParams.canByoYomiTime, genParams.nbMovesCanTime)
                "FISCHER" -> FischerTime(genParams.basicTime, genParams.fisherTime)
                else -> throw Error("missing byoyomi type")
            },
            pairing = when (handParams.hdCeiling) {
                0 -> Swiss(
                    when (pairingParams.paiMaSeedSystem1) {
                        "SPLITANDFOLD" -> Swiss.Method.SPLIT_AND_FOLD
                        "SPLITANDRANDOM" -> Swiss.Method.SPLIT_AND_RANDOM
                        "SPLITANDSLIP" -> Swiss.Method.SPLIT_AND_SLIP
                        else -> throw Error("unknown swiss pairing method")
                    },
                    when (pairingParams.paiMaSeedSystem2) {
                        "SPLITANDFOLD" -> Swiss.Method.SPLIT_AND_FOLD
                        "SPLITANDRANDOM" -> Swiss.Method.SPLIT_AND_RANDOM
                        "SPLITANDSLIP" -> Swiss.Method.SPLIT_AND_SLIP
                        else -> throw Error("unknown swiss pairing method")
                    }
                )
                else -> MacMahon() // TODO
            },
            rounds = genParams.numberOfRounds
        )
        val canonicMap = mutableMapOf<String, Int>()
        imported.Players.map { player ->
            Player(
                id = Store.nextPlayerId,
                name = player.name,
                firstname = player.firstName,
                rating = player.rating,
                rank = Pairable.parseRank(player.rank),
                country = player.country,
                club = player.club
            ).also {
                canonicMap.put("${player.name}${player.firstName}".uppercase(Locale.ENGLISH), it.id)
            }
        }.associateByTo(tournament.pairables) { it.id }
        val gamesPerRound = imported.Games.groupBy {
            it.roundNumber
        }.values.map {
            it.map { game ->
                Game(
                    id = Store.nextGameId,
                    black = canonicMap[game.blackPlayer] ?: throw Error("player not found: ${game.blackPlayer}"),
                    white = canonicMap[game.whitePlayer] ?: throw Error("player not found: ${game.whitePlayer}"),
                    handicap = game.handicap,
                    result = when (game.result) {
                        "RESULT_UNKNOWN" -> Game.Result.UNKNOWN
                        "RESULT_WHITEWINS" -> Game.Result.WHITE
                        "RESULT_BLACKWINS" -> Game.Result.BLACK
                        "RESULT_EQUAL" -> Game.Result.JIGO
                        "RESULT_BOTHWIN" -> Game.Result.BOTHWIN
                        "RESULT_BOTHLOOSE" -> Game.Result.BOTHLOOSE
                        else -> throw Error("unhandled result: ${game.result}")
                    }
                )
            }.associateBy { it.id }.toMutableMap()
        }
        tournament.games.addAll(gamesPerRound)
        return tournament
    }
}
