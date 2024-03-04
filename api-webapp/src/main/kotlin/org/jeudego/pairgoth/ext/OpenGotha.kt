package org.jeudego.pairgoth.ext

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import java.time.LocalDate
import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.opengotha.TournamentType
import org.jeudego.pairgoth.opengotha.ObjectFactory
import org.jeudego.pairgoth.store.Store
import org.jeudego.pairgoth.store.nextGameId
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.store.nextTournamentId
import org.w3c.dom.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.math.roundToInt

private const val MILLISECONDS_PER_DAY = 86400000
fun XMLGregorianCalendar.toLocalDate() = LocalDate.of(year, month, day)

object OpenGotha {

    private fun parseDrawUpDownMode(str: String) = when (str) {
        "BOT" -> MainCritParams.DrawUpDown.BOTTOM
        "MID" -> MainCritParams.DrawUpDown.MIDDLE
        "TOP" -> MainCritParams.DrawUpDown.TOP
        else -> throw Error("Invalid drawUpDown mode: $str")
    }

    private fun parseSeedSystem(str: String) = when (str) {
        "SPLITANDSLIP" -> MainCritParams.SeedMethod.SPLIT_AND_SLIP
        "SPLITANDRANDOM" -> MainCritParams.SeedMethod.SPLIT_AND_RANDOM
        "SPLITANDFOLD" -> MainCritParams.SeedMethod.SPLIT_AND_FOLD
        else -> throw Error("Invalid seed system: $str")
    }

    private fun String.titlecase(locale: Locale = Locale.ROOT) = lowercase(locale).replaceFirstChar { it.titlecase(locale) }

    private fun MainCritParams.SeedMethod.format() = toString().replace("_", "")

    fun import(element: Element): Tournament<*> {

        val context = JAXBContext.newInstance(ObjectFactory::class.java)
        val parsed = context.createUnmarshaller().unmarshal(element) as JAXBElement<TournamentType>
        val ogTournament = parsed.value

        // import tournament parameters

        val genParams = ogTournament.tournamentParameterSet.generalParameterSet
        val handParams = ogTournament.tournamentParameterSet.handicapParameterSet
        val placmtParams = ogTournament.tournamentParameterSet.placementParameterSet
        val pairParams = ogTournament.tournamentParameterSet.pairingParameterSet

        // some checks

        if (genParams.genNBW2ValueAbsent.toDouble() != 0.0) {
            throw Error("Pairgoth only support 0 for 'NBW for Absent player'")
        }

        if (genParams.genNBW2ValueBye.toDouble() != 2.0) {
            throw Error("Pairgoth only support 1 for 'NBW for Bye player'")
        }

        if (genParams.genMMS2ValueBye.toDouble() != 2.0) {
            throw Error("Pairgoth only support 1 for 'MMS for Bye player'")
        }

        val pairgothPairingParams = PairingParams(
            base = BaseCritParams(
                nx1 = pairParams.paiStandardNX1Factor.toDouble(),
                dupWeight = pairParams.paiBaAvoidDuplGame.toDouble(),
                random = pairParams.paiBaRandom.toDouble(),
                deterministic = pairParams.paiBaDeterministic.toBoolean(),
                colorBalanceWeight = pairParams.paiBaBalanceWB.toDouble()
            ),
            main = MainCritParams(
                categoriesWeight = pairParams.paiMaAvoidMixingCategories.toDouble(),
                scoreWeight = pairParams.paiMaMinimizeScoreDifference.toDouble(),
                drawUpDownWeight = pairParams.paiMaDUDDWeight.toDouble(),
                compensateDrawUpDown = pairParams.paiMaCompensateDUDD.toBoolean(),
                drawUpDownUpperMode = parseDrawUpDownMode(pairParams.paiMaDUDDUpperMode),
                drawUpDownLowerMode = parseDrawUpDownMode(pairParams.paiMaDUDDLowerMode),
                seedingWeight =  pairParams.paiMaMaximizeSeeding.toDouble(),
                lastRoundForSeedSystem1 = pairParams.paiMaLastRoundForSeedSystem1,
                seedSystem1 = parseSeedSystem(pairParams.paiMaSeedSystem1),
                seedSystem2 = parseSeedSystem(pairParams.paiMaSeedSystem2 ?: "SPLITANDSLIP"),
                additionalPlacementCritSystem1 = Criterion.valueOf(pairParams.paiMaAdditionalPlacementCritSystem1.uppercase()),
                additionalPlacementCritSystem2 = Criterion.valueOf(pairParams.paiMaAdditionalPlacementCritSystem2.uppercase().replace("NULL", "NONE")),
                mmsValueAbsent = genParams.genMMS2ValueAbsent.toDouble() / 2.0,
                roundDownScore = genParams.genRoundDownNBWMMS.toBoolean()
            ),
            secondary = SecondaryCritParams(
                barThresholdActive = pairParams.paiSeBarThresholdActive.toBoolean(),
                rankThreshold = Pairable.parseRank(pairParams.paiSeRankThreshold),
                nbWinsThresholdActive = pairParams.paiSeNbWinsThresholdActive.toBoolean(),
                defSecCrit = pairParams.paiSeDefSecCrit.toDouble()
            ),
            geo = GeographicalParams(
                avoidSameGeo = pairParams.paiSeAvoidSameGeo.toDouble(),
                preferMMSDiffRatherThanSameCountry = pairParams.paiSePreferMMSDiffRatherThanSameCountry,
                preferMMSDiffRatherThanSameClubsGroup = 2,
                preferMMSDiffRatherThanSameClub = pairParams.paiSePreferMMSDiffRatherThanSameClub
            ),
            handicap = HandicapParams(
                weight = pairParams.paiSeMinimizeHandicap.toDouble(),
                useMMS = handParams.hdBasedOnMMS.toBoolean(),
                rankThreshold = Pairable.parseRank(pairParams.paiSeRankThreshold),
                correction = handParams.hdCorrection,
                ceiling = handParams.hdCeiling
            )
        )

        val pairgothPlacementParams = PlacementParams(
            crit = placmtParams.placementCriteria.placementCriterion.filter {
                it.name != "NULL"
            }.map {
                Criterion.valueOf(it.name)
            }.toTypedArray()
        )

        val tournament = StandardTournament(
            id = nextTournamentId,
            type = Tournament.Type.INDIVIDUAL, // CB for now, TODO
            name = genParams.name,
            shortName = genParams.shortName,
            startDate = genParams.beginDate.toLocalDate(),
            endDate = genParams.endDate.toLocalDate(),
            country = "fr", // no country in opengotha format
            location = genParams.location,
            online = genParams.isBInternet ?: false,
            timeSystem = when (genParams.complementaryTimeSystem) {
                "SUDDENDEATH" -> SuddenDeath(genParams.basicTime * 60)
                "STDBYOYOMI" -> StandardByoyomi(genParams.basicTime * 60, genParams.stdByoYomiTime, 1) // no periods?
                "CANBYOYOMI" -> CanadianByoyomi(genParams.basicTime * 60, genParams.canByoYomiTime, genParams.nbMovesCanTime)
                "FISCHER" -> FischerTime(genParams.basicTime * 60, genParams.fischerTime)
                else -> throw Error("missing byoyomi type")
            },
            pairing = when (handParams.hdCeiling) {
                0 -> Swiss(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams
                )
                else -> MacMahon(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams,
                    mmFloor = Pairable.parseRank(genParams.genMMFloor),
                    mmBar = Pairable.parseRank(genParams.genMMBar)
                )
            },
            rounds = genParams.numberOfRounds
        )

        val canonicMap = mutableMapOf<String, Int>()
        // import players
        ogTournament.players.player.map { player ->
            Player(
                id = nextPlayerId,
                name = player.name,
                firstname = player.firstName,
                rating = player.rating,
                rank = Pairable.parseRank(player.rank),
                country = player.country,
                club = player.club,
                final = "FIN" == player.registeringStatus,
                mmsCorrection = player.smmsCorrection
            ).also {
                player.participating.toString().forEachIndexed { i,c ->
                    if (c == '0') it.skip.add(i + 1)
                }
                canonicMap.put("${player.name.replace(" ", "")}${player.firstName.replace(" ", "")}".uppercase(Locale.ENGLISH), it.id)
            }
        }.associateByTo(tournament.players) { it.id }
        val gamesPerRound = ogTournament.games.game.groupBy {
            it.roundNumber
        }.entries.sortedBy { it.key }.map {
            it.value.map { game ->
                Game(
                    id = nextGameId,
                    table = game.tableNumber,
                    black = canonicMap[game.blackPlayer] ?: throw Error("player not found: ${game.blackPlayer}"),
                    white = canonicMap[game.whitePlayer] ?: throw Error("player not found: ${game.whitePlayer}"),
                    handicap = game.handicap,
                    result = when (game.result.removeSuffix("_BYDEF")) {
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
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <Tournament fullVersionNumber="3.52.03" runningMode="SAL" saveDT="$now">
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
                    }" ratingOrigin="" registeringStatus="${
                        if (player.final) "FIN" else "PRE"
                    }" smmsCorrection="${
                        player.mmsCorrection
                    }"/>"""
                }
            }
            </Players>
            <Games>
            // TODO - table number is not any more kinda random like this
            ${(1..tournament.lastRound()).map { tournament.games(it) }.flatMapIndexed { index, games ->
                    games.values.mapNotNull { game ->
                        if (game.black == 0 || game.white == 0) null
                        else Pair(index + 1, game)
                    }
                }.joinToString("\n") { (round, game) ->
                    """<Game blackPlayer="${
                        (tournament.pairables[game.black]!! as Player).let { black ->
                            "${black.name.replace(" ", "")}${black.firstname.replace(" ", "")}".uppercase(Locale.ENGLISH) // Use Locale.ENGLISH to transform é to É    
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
                        game.table
                    }" whitePlayer="${
                        (tournament.pairables[game.white]!! as Player).let { white ->
                            "${white.name}${white.firstname}".uppercase(Locale.ENGLISH) // Use Locale.ENGLISH to transform é to É    
                        }
                    }"/>"""
                }
            }
            </Games>
            <ByePlayer>
            ${
                (1..tournament.lastRound()).map { round ->
                    tournament.games(round).values.firstNotNullOfOrNull { g -> 
                        if (g.black == 0 || g.white == 0) g else null
                    }?.let {
                        tournament.pairables[
                            if (it.black == 0) it.white
                            else it.black
                        ] as Player
                    }?.let { p ->
                        "<ByePlayer player=\"${p.name.replace(" ", "")}${p.firstname.replace(" ", "")}\" roundNumber=\"${round}\"/>"
                    }
                }.joinToString("\n")
            }
            </ByePlayer>
            <TournamentParameterSet>
            <GeneralParameterSet bInternet="${tournament.online}" basicTime="${tournament.timeSystem.mainTime / 60}" beginDate="${tournament.startDate}" canByoYomiTime="${tournament.timeSystem.byoyomi}" complementaryTimeSystem="${when(tournament.timeSystem.type) {
                TimeSystem.TimeSystemType.SUDDEN_DEATH -> "SUDDENDEATH"
                TimeSystem.TimeSystemType.JAPANESE -> "STDBYOYOMI"
                TimeSystem.TimeSystemType.CANADIAN -> "CANBYOYOMI"
                TimeSystem.TimeSystemType.FISCHER -> "FISCHER"
            } }" director="" endDate="${tournament.endDate}" fischerTime="${tournament.timeSystem.increment}" genCountNotPlayedGamesAsHalfPoint="false" genMMBar="${
                displayRank(
                    if (tournament.pairing is MacMahon) tournament.pairing.mmBar else 8
                ).uppercase(Locale.ROOT)
            }" genMMFloor="${
                displayRank(
                    if (tournament.pairing is MacMahon) tournament.pairing.mmFloor else -30
                ).uppercase(Locale.ROOT)
            }" genMMS2ValueAbsent="${
            (tournament.pairing.pairingParams.main.mmsValueAbsent * 2).roundToInt()
            }" genMMS2ValueBye="2" genMMZero="30K" genNBW2ValueAbsent="0" genNBW2ValueBye="2" genRoundDownNBWMMS="${
                tournament.pairing.pairingParams.main.roundDownScore
            }" komi="${tournament.komi}" location="${tournament.location}" name="${tournament.name}" nbMovesCanTime="${tournament.timeSystem.stones}" numberOfCategories="1" numberOfRounds="${tournament.rounds}" shortName="${tournament.shortName}" size="${tournament.gobanSize}" stdByoYomiTime="${tournament.timeSystem.byoyomi}"/>
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
            <PairingParameterSet paiBaAvoidDuplGame="${tournament.pairing.pairingParams.base.dupWeight.toLong()}" paiBaBalanceWB="${tournament.pairing.pairingParams.base.colorBalanceWeight.toLong()}" paiBaDeterministic="${tournament.pairing.pairingParams.base.deterministic}" paiBaRandom="${tournament.pairing.pairingParams.base.random.toLong()}" paiMaAdditionalPlacementCritSystem1="${tournament.pairing.pairingParams.main.additionalPlacementCritSystem1.toString().titlecase()}" paiMaAdditionalPlacementCritSystem2="${tournament.pairing.pairingParams.main.additionalPlacementCritSystem2.toString().titlecase()}" paiMaAvoidMixingCategories="${tournament.pairing.pairingParams.main.categoriesWeight.toLong()}" paiMaCompensateDUDD="${tournament.pairing.pairingParams.main.compensateDrawUpDown}" paiMaDUDDLowerMode="${tournament.pairing.pairingParams.main.drawUpDownLowerMode.toString().substring(0, 3)}" paiMaDUDDUpperMode="${tournament.pairing.pairingParams.main.drawUpDownUpperMode.toString().substring(0, 3)}" paiMaDUDDWeight="${tournament.pairing.pairingParams.main.drawUpDownWeight.toLong()}" paiMaLastRoundForSeedSystem1="${tournament.pairing.pairingParams.main.lastRoundForSeedSystem1}" paiMaMaximizeSeeding="${tournament.pairing.pairingParams.main.seedingWeight.toLong()}" paiMaMinimizeScoreDifference="${tournament.pairing.pairingParams.main.scoreWeight.toLong()}" paiMaSeedSystem1="${tournament.pairing.pairingParams.main.seedSystem1.format()}" paiMaSeedSystem2="${tournament.pairing.pairingParams.main.seedSystem2.format()}" paiSeAvoidSameGeo="${tournament.pairing.pairingParams.geo.avoidSameGeo.toLong()}" paiSeBarThresholdActive="${tournament.pairing.pairingParams.secondary.barThresholdActive}" paiSeDefSecCrit="${tournament.pairing.pairingParams.secondary.defSecCrit.toLong()}" paiSeMinimizeHandicap="${tournament.pairing.pairingParams.handicap.weight.toLong()}" paiSeNbWinsThresholdActive="${tournament.pairing.pairingParams.secondary.nbWinsThresholdActive}" paiSePreferMMSDiffRatherThanSameClub="${tournament.pairing.pairingParams.geo.preferMMSDiffRatherThanSameClub}" paiSePreferMMSDiffRatherThanSameCountry="${tournament.pairing.pairingParams.geo.preferMMSDiffRatherThanSameCountry}" paiSeRankThreshold="${displayRank(tournament.pairing.pairingParams.secondary.rankThreshold).uppercase()}" paiStandardNX1Factor="${tournament.pairing.pairingParams.base.nx1}"/>
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
