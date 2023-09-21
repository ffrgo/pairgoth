package org.jeudego.pairgoth.ext

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import kotlinx.datetime.LocalDate
import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.opengotha.TournamentType
import org.jeudego.pairgoth.opengotha.ObjectFactory
import org.jeudego.pairgoth.store.Store
import org.w3c.dom.Element
import java.util.*
import javax.xml.datatype.XMLGregorianCalendar

private const val MILLISECONDS_PER_DAY = 86400000
fun XMLGregorianCalendar.toLocalDate() = LocalDate.fromEpochDays((toGregorianCalendar().time.time / MILLISECONDS_PER_DAY).toInt())

object OpenGotha {
    fun import(element: Element): Tournament<*> {

        val context = JAXBContext.newInstance(ObjectFactory::class.java)
        val parsed = context.createUnmarshaller().unmarshal(element) as JAXBElement<TournamentType>
        val ogTournament = parsed.value

        // import tournament parameters

        val genParams = ogTournament.tournamentParameterSet.generalParameterSet
        val handParams = ogTournament.tournamentParameterSet.handicapParameterSet
        val placmtParams = ogTournament.tournamentParameterSet.placementParameterSet
        val pairParams = ogTournament.tournamentParameterSet.pairingParameterSet

        val tournament = StandardTournament(
            id = Store.nextTournamentId,
            type = Tournament.Type.INDIVIDUAL, // CB for now, TODO
            name = genParams.name,
            shortName = genParams.shortName,
            startDate = genParams.beginDate.toLocalDate(),
            endDate = genParams.endDate.toLocalDate(),
            country = "FR", // no country in opengotha format
            location = genParams.location,
            online = genParams.isBInternet ?: false,
            timeSystem = when (genParams.complementaryTimeSystem) {
                "SUDDENDEATH" -> SuddenDeath(genParams.basicTime)
                "STDBYOYOMI" -> StandardByoyomi(genParams.basicTime, genParams.stdByoYomiTime, 1) // no periods?
                "CANBYOYOMI" -> CanadianByoyomi(genParams.basicTime, genParams.canByoYomiTime, genParams.nbMovesCanTime)
                "FISCHER" -> FischerTime(genParams.basicTime, genParams.fischerTime)
                else -> throw Error("missing byoyomi type")
            },
            pairing = when (handParams.hdCeiling) {
                0 -> Swiss(
                    pairingParams = PairingParams(

                    ),
                    placementParams = PlacementParams(
                        crit = placmtParams.placementCriteria.placementCriterion.filter {
                            it.name != "NULL"
                        }.map {
                            Criterion.valueOf(it.name)
                        }.toTypedArray()
                    )
                ) // TODO
                else -> MacMahon(
                    pairingParams = PairingParams(

                    ),
                    placementParams = PlacementParams(

                    )
                ) // TODO
            },
            rounds = genParams.numberOfRounds
        )

        val canonicMap = mutableMapOf<String, Int>()
        // import players
        ogTournament.players.player.map { player ->
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
        val gamesPerRound = ogTournament.games.game.groupBy {
            it.roundNumber
        }.entries.sortedBy { it.key }.map {
            it.value.map { game ->
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
        gamesPerRound.forEachIndexed { index, games ->
            tournament.games(index + 1).putAll(games)
        }
        return tournament
    }

    // TODO - bye player(s)
    fun export(tournament: Tournament<*>): String {
        // two methods here
        // method 1 (advised because it's more error-proof but more complex to set up) is to assign one by one
        // the fields of an OGTournamentType instance, then call toPrettyString() on it
        // val ogt = OGTournamentType()
        // ...
        //
        // method 2 (quick and dirty) is to rely on templating:
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
            ${(1..tournament.lastRound()).map { tournament.games(it) }.flatMapIndexed { index, games ->
                    games.values.mapIndexed { table, game ->
                        Triple(index + 1, table , game)
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
                        round
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
            <HandicapParameterSet hdBasedOnMMS="${tournament.pairing.pairingParams.handicap.useMMS}" hdCeiling="${tournament.pairing.pairingParams.handicap.ceiling}" hdCorrection="${tournament.pairing.pairingParams.handicap.correction}" hdNoHdRankThreshold="${displayRank(tournament.pairing.pairingParams.handicap.rankThreshold)}"/>
            <PlacementParameterSet>
            <PlacementCriteria>
            ${
                (0..5).map {
                    """<PlacementCriterion name="${tournament.pairing.placementParams.criteria.getOrNull(it)?.name ?: "NULL"}" number="${it + 1}"/>"""
                }
            }
            </PlacementCriteria>
            </PlacementParameterSet>
            <PairingParameterSet paiBaAvoidDuplGame="${tournament.pairing.pairingParams.base.dupWeight.toInt()}" paiBaBalanceWB="${tournament.pairing.pairingParams.base.colorBalanceWeight.toInt()}" paiBaDeterministic="${tournament.pairing.pairingParams.base.deterministic}" paiBaRandom="${tournament.pairing.pairingParams.base.random.toInt()}" paiMaAdditionalPlacementCritSystem1="Rating" paiMaAdditionalPlacementCritSystem2="Rating" paiMaAvoidMixingCategories="0" paiMaCompensateDUDD="true" paiMaDUDDLowerMode="MID" paiMaDUDDUpperMode="MID" paiMaDUDDWeight="100000000" paiMaLastRoundForSeedSystem1="2" paiMaMaximizeSeeding="5000000" paiMaMinimizeScoreDifference="100000000000" paiMaSeedSystem1="SPLITANDSLIP" paiMaSeedSystem2="SPLITANDSLIP" paiSeAvoidSameGeo="0" paiSeBarThresholdActive="true" paiSeDefSecCrit="20000000000000" paiSeMinimizeHandicap="0" paiSeNbWinsThresholdActive="true" paiSePreferMMSDiffRatherThanSameClub="0" paiSePreferMMSDiffRatherThanSameCountry="0" paiSeRankThreshold="30K" paiStandardNX1Factor="0.5"/>
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
