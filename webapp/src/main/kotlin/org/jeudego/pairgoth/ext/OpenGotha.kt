package org.jeudego.pairgoth.ext

import org.jeudego.pairgoth.model.CanadianByoyomi
import org.jeudego.pairgoth.model.FischerTime
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.MacMahon
import org.jeudego.pairgoth.model.Pairable
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.StandardByoyomi
import org.jeudego.pairgoth.model.StandardTournament
import org.jeudego.pairgoth.model.SuddenDeath
import org.jeudego.pairgoth.model.Swiss
import org.jeudego.pairgoth.model.TimeSystem
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.displayRank
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
    fun import(element: Element): Tournament<*> {
        val imported = OpenGothaFormat(element)
        val genParams = imported.TournamentParameterSet.GeneralParameterSet
        val handParams = imported.TournamentParameterSet.HandicapParameterSet
        val pairingParams = imported.TournamentParameterSet.PairingParameterSet
        val tournament = StandardTournament(
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
        }.associateByTo(tournament.players) { it.id }
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

    // TODO - bye player(s)
    fun export(tournament: Tournament<*>): String {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <Tournament dataVersion="201" externalIPAddress="88.122.144.219" fullVersionNumber="3.51" runningMode="SAL" saveDT="20210111180800">
            <Players>
            ${tournament.pairables.values.map { player ->
                    player as Player
                }.joinToString("\n") { player -> 
                    """<Player agaExpirationDate="" agaId="" club="${
                        player.club
                    }" country="${
                        player.country
                    }" egfPin="" ffgLicence="" ffgLicenceStatus="" firstName="${
                        player.firstname
                    }" grade="${
                        player.displayRank()
                    }" name="${
                        player.name
                    }" participating="${
                        (1..20).map { 
                            if (player.skip.contains(it)) 0 else 1 
                        }.joinToString("") 
                    }" rank="${
                        player.displayRank()
                    }" rating="${
                        player.rating
                    }" ratingOrigin="" registeringStatus="FIN" smmsCorrection="0"/>"""
                }
            }
            </Players>
            <Games>
            ${tournament.games.flatMapIndexed { round, games ->
                    games.values.mapIndexed { table, game ->
                        Triple(round, table , game)
                    }
                }.joinToString("\n") { (round, table, game) ->
                    """<Game blackPlayer="${
                        (tournament.pairables[game.black]!! as Player).let { black ->
                            "${black.name}${black.firstname}".uppercase(Locale.ENGLISH) // Use Locale.ENGLISH to transform é to É    
                        }
                    }" handicap="0" knownColor="true" result="${
                        when (game.result) {
                            Game.Result.UNKNOWN, Game.Result.CANCELLED -> "RESULT_UNKNOWN"
                            Game.Result.BLACK -> "RESULT_BLACKWINS"
                            Game.Result.WHITE -> "RESULT_WHITEWINS"
                            Game.Result.JIGO -> "RESULT_EQUAL"
                            Game.Result.BOTHWIN -> "RESULT_BOTHWIN"
                            Game.Result.BOTHLOOSE -> "RESULT_BOTHLOOSE"
                        }
                    }" roundNumber="${
                        round + 1
                    }" tableNumber="${
                        table + 1
                    }" whitePlayer="${
                        (tournament.pairables[game.white]!! as Player).let { white ->
                            "${white.name}${white.firstname}".uppercase(Locale.ENGLISH) // Use Locale.ENGLISH to transform é to É    
                        }
                    }"/>"""
                }
            }
            </Games>
            <ByePlayer>
            </ByePlayer>
            <TournamentParameterSet>
            <GeneralParameterSet bInternet="${tournament.online}" basicTime="${tournament.timeSystem.mainTime}" beginDate="${tournament.startDate}" canByoYomiTime="${tournament.timeSystem.byoyomi}" complementaryTimeSystem="${when(tournament.timeSystem.type) {
                TimeSystem.TimeSystemType.SUDDEN_DEATH -> "SUDDENDEATH"
                TimeSystem.TimeSystemType.STANDARD -> "STDBYOYOMI"
                TimeSystem.TimeSystemType.CANADIAN -> "CANBYOYOMI"
                TimeSystem.TimeSystemType.FISCHER -> "FISCHER"
            } }" director="" endDate="${tournament.endDate}" fischerTime="${tournament.timeSystem.increment}" genCountNotPlayedGamesAsHalfPoint="false" genMMBar="9D" genMMFloor="30K" genMMS2ValueAbsent="1" genMMS2ValueBye="2" genMMZero="30K" genNBW2ValueAbsent="0" genNBW2ValueBye="2" genRoundDownNBWMMS="true" komi="${tournament.komi}" location="${tournament.location}" name="${tournament.name}" nbMovesCanTime="${tournament.timeSystem.stones}" numberOfCategories="1" numberOfRounds="${tournament.rounds}" shortName="${tournament.shortName}" size="${tournament.gobanSize}" stdByoYomiTime="${tournament.timeSystem.byoyomi}"/>
            <HandicapParameterSet hdBasedOnMMS="false" hdCeiling="0" hdCorrection="0" hdNoHdRankThreshold="30K"/>
            <PlacementParameterSet>
            <PlacementCriteria>
            <PlacementCriterion name="NBW" number="1"/>
            <PlacementCriterion name="SOSW" number="2"/>
            <PlacementCriterion name="SOSOSW" number="3"/>
            <PlacementCriterion name="NULL" number="4"/>
            <PlacementCriterion name="NULL" number="5"/>
            <PlacementCriterion name="NULL" number="6"/>
            </PlacementCriteria>
            </PlacementParameterSet>
            <PairingParameterSet paiBaAvoidDuplGame="500000000000000" paiBaBalanceWB="1000000" paiBaDeterministic="true" paiBaRandom="0" paiMaAdditionalPlacementCritSystem1="Rating" paiMaAdditionalPlacementCritSystem2="Rating" paiMaAvoidMixingCategories="0" paiMaCompensateDUDD="true" paiMaDUDDLowerMode="MID" paiMaDUDDUpperMode="MID" paiMaDUDDWeight="100000000" paiMaLastRoundForSeedSystem1="2" paiMaMaximizeSeeding="5000000" paiMaMinimizeScoreDifference="100000000000" paiMaSeedSystem1="SPLITANDSLIP" paiMaSeedSystem2="SPLITANDSLIP" paiSeAvoidSameGeo="0" paiSeBarThresholdActive="true" paiSeDefSecCrit="20000000000000" paiSeMinimizeHandicap="0" paiSeNbWinsThresholdActive="true" paiSePreferMMSDiffRatherThanSameClub="0" paiSePreferMMSDiffRatherThanSameCountry="0" paiSeRankThreshold="30K" paiStandardNX1Factor="0.5"/>
            <DPParameterSet displayClCol="true" displayCoCol="true" displayIndGamesInMatches="true" displayNPPlayers="false" displayNumCol="true" displayPlCol="true" gameFormat="short" playerSortType="name" showByePlayer="true" showNotFinallyRegisteredPlayers="true" showNotPairedPlayers="true" showNotParticipatingPlayers="false" showPlayerClub="true" showPlayerCountry="false" showPlayerGrade="true"/>
            <PublishParameterSet exportToLocalFile="true" htmlAutoScroll="false" print="false"/>
            </TournamentParameterSet>
            <TeamTournamentParameterSet>
            <TeamGeneralParameterSet teamSize="4"/>
            <TeamPlacementParameterSet>
            <PlacementCriteria>
            <PlacementCriterion name="TEAMP" number="1"/>
            <PlacementCriterion name="BDW" number="2"/>
            <PlacementCriterion name="BDW3U" number="3"/>
            <PlacementCriterion name="BDW2U" number="4"/>
            <PlacementCriterion name="BDW1U" number="5"/>
            <PlacementCriterion name="MNR" number="6"/>
            </PlacementCriteria>
            </TeamPlacementParameterSet>
            </TeamTournamentParameterSet>
            </Tournament>
            
        """.trimIndent()
        return xml
    }
}
