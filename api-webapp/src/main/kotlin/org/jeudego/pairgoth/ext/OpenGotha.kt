package org.jeudego.pairgoth.ext

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import org.apache.commons.text.StringEscapeUtils
import java.time.LocalDate
import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.opengotha.TournamentType
import org.jeudego.pairgoth.opengotha.ObjectFactory
import org.jeudego.pairgoth.store.nextGameId
import org.jeudego.pairgoth.store.nextPlayerId
import org.jeudego.pairgoth.store.nextTournamentId
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.math.roundToInt

private const val MILLISECONDS_PER_DAY = 86400000
fun XMLGregorianCalendar.toLocalDate() = LocalDate.of(year, month, day)

object OpenGotha {
    private val logger = LoggerFactory.getLogger("OpenGotha")
    private fun parseRankOrDefault(rankStr: String, default: Int, ctx: String): Int =
        Pairable.parseRank(rankStr) ?: default.also {
            logger.warn("invalid rank '$rankStr' for $ctx; defaulting to ${displayRank(default)}")
        }

    // Returns (rank, pro) for amateur or pro inputs; falls back to (default, 0) on garbage.
    private fun parseRankAndProOrDefault(rankStr: String, default: Int, ctx: String): Pair<Int, Int> =
        Pairable.parseRankAndPro(rankStr) ?: Pair(default, 0).also {
            logger.warn("invalid rank '$rankStr' for $ctx; defaulting to ${displayRank(default)}")
        }

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

    private fun String.titleCase(locale: Locale = Locale.ROOT) = lowercase(locale).replaceFirstChar { it.titlecase(locale) }
    private fun String.escapeXML() = StringEscapeUtils.escapeXml11(this)

    private fun MainCritParams.SeedMethod.format() = toString().replace("_", "")

    // OpenGotha's Player.computeKeyString; Locale.ENGLISH to transform é to É
    private fun key(name: String, firstname: String) = "$name$firstname".replace(" ", "").uppercase(Locale.ENGLISH)
    private val Player.key get() = key(name, firstname)

    // Pairgo and rengo have no OpenGotha counterpart: each team is exported as one player named
    // after its members. OpenGotha shows "name firstname", hence the split at the last member.
    private fun teamsAsPlayers(tournament: TeamTournament): Map<ID, Player> {
        val keys = mutableSetOf<String>()
        return tournament.teams.values.associate { team ->
            val members = team.teamPlayers.toList()
            val labels = members.map { if (it.firstname.isBlank()) it.name else "${it.name} ${it.firstname.first()}." }
            val (name, firstname) = when (members.size) {
                0 -> team.name to "-"
                1 -> members[0].name to members[0].firstname.ifBlank { "-" }
                else -> labels.dropLast(1).joinToString(" - ", postfix = " -") to labels.last()
            }
            var unique = firstname
            var n = 1
            while (!keys.add(key(name, unique))) unique = "$firstname (${++n})"
            team.id to Player(team.id, name, unique, team.rating, team.rank, team.country ?: "", team.club ?: "", team.final, team.mmsCorrection).also { player ->
                (1..tournament.rounds).filterNot { team.canPlay(it) }.forEach { player.skip.add(it) }
            }
        }
    }

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
                nbwValueAbsent = genParams.genNBW2ValueAbsent.toDouble() / 2.0,
                roundDownScore = genParams.genRoundDownNBWMMS.toBoolean()
            ),
            secondary = SecondaryCritParams(
                barThresholdActive = pairParams.paiSeBarThresholdActive.toBoolean(),
                rankSecThreshold = parseRankOrDefault(pairParams.paiSeRankThreshold, -20, "secondary rank threshold"),
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
                rankThreshold = parseRankOrDefault(handParams.hdNoHdRankThreshold, -20, "handicap rank threshold"),
                correction = handParams.hdCorrection,
                ceiling = handParams.hdCeiling
            )
        )

        val pairgothPlacementParams = PlacementParams(
            crit = placmtParams.placementCriteria.placementCriterion.filter {
                it.name != "NULL"
            }.map {
                Criterion.valueOf(it.name.uppercase())
            }.toTypedArray()
        )

        // country, rules and pairing type are not part of the OpenGotha format: our own exports carry them
        val country = genParams.country?.takeUnless { it.isBlank() }?.lowercase(Locale.ROOT)
            ?: ogTournament.players.player.mapNotNull { it.country?.takeUnless { c -> c.isBlank() }?.lowercase(Locale.ROOT) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key // most common among the players
            ?: "eu"
        val rules = Rules.values().firstOrNull { it.name == genParams.rules }
            ?: if (country == "fr") Rules.FRENCH else Rules.AGA
        // without it, the system is the one OpenGotha itself infers from the first placement criterion
        val pairingType = PairingType.values().firstOrNull { it.name == genParams.pairingType }
            ?: if (pairgothPlacementParams.criteria.firstOrNull() == Criterion.MMS) PairingType.MAC_MAHON else PairingType.SWISS

        val tournament = StandardTournament(
            id = nextTournamentId,
            type = Tournament.Type.INDIVIDUAL, // CB for now, TODO
            name = genParams.name,
            shortName = genParams.shortName,
            startDate = genParams.beginDate.toLocalDate(),
            endDate = genParams.endDate.toLocalDate(),
            director = genParams.director,
            country = country,
            location = genParams.location,
            online = genParams.isBInternet ?: false,
            timeSystem = when (genParams.complementaryTimeSystem) {
                "SUDDENDEATH" -> SuddenDeath(genParams.basicTime * 60)
                "STDBYOYOMI" -> StandardByoyomi(genParams.basicTime * 60, genParams.stdByoYomiTime, 1) // no periods?
                "CANBYOYOMI" -> CanadianByoyomi(genParams.basicTime * 60, genParams.canByoYomiTime, genParams.nbMovesCanTime)
                "FISCHER" -> FischerTime(genParams.basicTime * 60, genParams.fischerTime)
                else -> throw Error("missing byoyomi type")
            },
            pairing = when (pairingType) {
                PairingType.MAC_MAHON -> MacMahon(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams,
                    mmFloor = parseRankOrDefault(genParams.genMMFloor, -30, "MM floor"),
                    mmBar = parseRankOrDefault(genParams.genMMBar, 0, "MM bar")
                )
                PairingType.FRENCH -> French(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams
                )
                PairingType.ROUND_ROBIN -> RoundRobin(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams
                )
                PairingType.SWISS -> Swiss(
                    pairingParams = pairgothPairingParams,
                    placementParams = pairgothPlacementParams
                )
            },
            rounds = genParams.numberOfRounds,
            rules = rules,
            gobanSize = genParams.size ?: 19,
            komi = genParams.komi?.toString()?.toDouble() ?: 7.5
        )

        val canonicMap = mutableMapOf<String, Int>()
        // import players
        ogTournament.players.player.map { player ->
            // OpenGotha `grade` is the honorary value (our stored `rank`); OG `rank` is its
            // effective pairing rank, which we recompute from rating — warn if they disagree.
            val gradeStr = player.grade.takeUnless { it.isNullOrEmpty() } ?: player.rank
            val (rank, pro) = parseRankAndProOrDefault(gradeStr, -20, "player ${player.name} ${player.firstName}")
            Pairable.parseRank(player.rank)?.let { ogEffective ->
                val derived = Pairable.ratingToRank(player.rating)
                if (ogEffective != derived) logger.warn("player ${player.name} ${player.firstName}: OpenGotha rank ${player.rank} differs from rating-derived ${displayRank(derived)} — pairing will use the rating-derived rank")
            }
            Player(
                id = nextPlayerId,
                name = player.name,
                firstname = player.firstName,
                rating = player.rating,
                rank = rank,
                country = player.country,
                club = player.club,
                final = "FIN" == player.registeringStatus,
                mmsCorrection = player.smmsCorrection,
                pro = pro
            ).also {
                player.participating.forEachIndexed { i,c ->
                    if (c == '0') it.skip.add(i + 1)
                }
                if (player.ffgLicence.isNotEmpty()) {
                    it.externalIds[DatabaseId.FFG] = player.ffgLicence
                }
                player.ffgLicenceStatus?.takeUnless { s -> s.isEmpty() }?.let { s -> it.licensed = (s == "L") }
                if (player.egfPin.isNotEmpty()) {
                    it.externalIds[DatabaseId.EGF] = player.egfPin
                }
                it.externalIds
                canonicMap.put(key(player.name, player.firstName), it.id)
            }
        }.associateByTo(tournament.players) { it.id }
        // import games
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
                        "RESULT_BOTHLOSE", "RESULT_BOTHLOOSE" -> Game.Result.BOTHLOOSE // the latter from our exports up to 0.28
                        else -> throw Error("unhandled result: ${game.result}")
                    }
                )
            }.associateBy { it.id }.toMutableMap()
        }
        gamesPerRound.forEachIndexed { index, games ->
            tournament.games(index + 1).putAll(games)
        }
        // import bye players
        if (ogTournament.byePlayers != null) {
            for (byePlayer in ogTournament.byePlayers.byePlayer) {
                val playerId = canonicMap[byePlayer.player] ?: throw Error("player not found: ${byePlayer.player}")
                val round = byePlayer.roundNumber
                val game = Game(id = nextGameId, table = 0, white = playerId, black = 0, result = Game.Result.WHITE)
                tournament.games(round)[game.id] = game
            }
        }

        return tournament
    }

    fun export(tournament: Tournament<*>): String {
        // two methods here
        // method 1 (advised because it's more error-proof but more complex to set up) is to assign one by one
        // the fields of an OGTournamentType instance, then call toPrettyString() on it
        // val ogt = OGTournamentType()
        // ...
        //
        // method 2 (quick and dirty) is to rely on templating:
        val players: Map<ID, Player> = when {
            tournament !is TeamTournament -> tournament.players
            tournament.type.individual -> throw Error("OpenGotha export is not available yet for team tournaments")
            else -> teamsAsPlayers(tournament)
        }
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <Tournament fullVersionNumber="3.52.03" runningMode="SAL" saveDT="$now" dataVersion="201">
            <Players>
            ${players.values.joinToString("\n") { player -> 
                    """<Player agaExpirationDate="" agaId="" club="${
                        player.club.escapeXML()
                    }" country="${
                        player.country
                    }" egfPin="${
                        player.externalIds[DatabaseId.EGF] ?: ""
                    }" ffgLicence="${
                        player.externalIds[DatabaseId.FFG] ?: ""
                    }" ffgLicenceStatus="${when (player.licensed) { true -> "L"; false -> "-"; null -> "" }}" firstName="${
                        player.firstname.escapeXML()
                    }" grade="${
                        player.displayRank()
                    }" name="${
                        player.name.escapeXML()
                    }" participating="${
                        (1..20).map { 
                            if (player.skip.contains(it)) 0 else 1 
                        }.joinToString("") 
                    }" rank="${
                        player.displayEffectiveRank()
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
            ${(1..tournament.lastRound()).map { tournament.games(it) }.flatMapIndexed { index, games ->
                    games.values.mapNotNull { game ->
                        if (game.black == 0 || game.white == 0) null
                        else Pair(index + 1, game)
                    }
                }.joinToString("\n") { (round, game) ->
                    """<Game blackPlayer="${
                        players[game.black]!!.key.escapeXML()
                    }" handicap="${
                        game.handicap
                    }" knownColor="true" result="${
                        when (game.result) {
                            Game.Result.UNKNOWN, Game.Result.CANCELLED -> "RESULT_UNKNOWN"
                            Game.Result.BLACK -> "RESULT_BLACKWINS"
                            Game.Result.WHITE -> "RESULT_WHITEWINS"
                            Game.Result.JIGO -> "RESULT_EQUAL"
                            Game.Result.BOTHWIN -> "RESULT_BOTHWIN"
                            Game.Result.BOTHLOOSE -> "RESULT_BOTHLOSE"
                        }
                    }" roundNumber="${
                        round
                    }" tableNumber="${
                        game.table
                    }" whitePlayer="${
                        players[game.white]!!.key.escapeXML()
                    }"/>"""
                }
            }
            </Games>
            <ByePlayers>
            ${
                (1..tournament.lastRound()).map { round ->
                    tournament.games(round).values.filter { g ->
                        g.black == 0 || g.white == 0
                    }.map {
                        players[if (it.black == 0) it.white else it.black]!!
                    }.map { p ->
                        "<ByePlayer player=\"${p.key.escapeXML()}\" roundNumber=\"${round}\"/>"
                    }.joinToString("\n")
                }.joinToString("\n")
            }
            </ByePlayers>
            <TournamentParameterSet>
            <GeneralParameterSet bInternet="${
                tournament.online
            }" basicTime="${
                tournament.timeSystem.mainTime / 60
            }" beginDate="${
                tournament.startDate
            }" canByoYomiTime="${
                tournament.timeSystem.byoyomi
            }" complementaryTimeSystem="${
                when(tournament.timeSystem.type) {
                TimeSystem.TimeSystemType.SUDDEN_DEATH -> "SUDDENDEATH"
                TimeSystem.TimeSystemType.JAPANESE -> "STDBYOYOMI"
                TimeSystem.TimeSystemType.CANADIAN -> "CANBYOYOMI"
                TimeSystem.TimeSystemType.FISCHER -> "FISCHER"
            } }" country="${
                tournament.country
            }" director="${
                tournament.director.escapeXML()
            }" endDate="${
                tournament.endDate
            }" fischerTime="${
                tournament.timeSystem.increment
            }" genCountNotPlayedGamesAsHalfPoint="false" genMMBar="${
                displayRank(
                    if (tournament.pairing is MacMahon) tournament.pairing.mmBar else 8
                ).uppercase(Locale.ROOT)
            }" genMMFloor="${
                displayRank(
                    if (tournament.pairing is MacMahon) tournament.pairing.mmFloor else -30
                ).uppercase(Locale.ROOT)
            }" genMMS2ValueAbsent="${
            (tournament.pairing.pairingParams.main.mmsValueAbsent * 2).roundToInt()
            }" genMMS2ValueBye="2" genMMZero="30K" genNBW2ValueAbsent="${
                (tournament.pairing.pairingParams.main.nbwValueAbsent * 2).roundToInt()
            }" genNBW2ValueBye="2" genRoundDownNBWMMS="${
                tournament.pairing.pairingParams.main.roundDownScore
            }" komi="${
                tournament.komi
            }" location="${
                tournament.location.escapeXML()
            }" name="${
                tournament.name.escapeXML()
            }" nbMovesCanTime="${
                tournament.timeSystem.stones
            }" numberOfCategories="1" numberOfRounds="${
                tournament.rounds
            }" pairingType="${
                tournament.pairing.type
            }" rules="${
                tournament.rules
            }" shortName="${
                tournament.shortName
            }" size="${
                tournament.gobanSize
            }" stdByoYomiTime="${
                tournament.timeSystem.byoyomi
            }"/>
            <HandicapParameterSet hdBasedOnMMS="${
                tournament.pairing.pairingParams.handicap.useMMS
            }" hdCeiling="${
                tournament.pairing.pairingParams.handicap.ceiling
            }" hdCorrection="${
                tournament.pairing.pairingParams.handicap.correction
            }" hdNoHdRankThreshold="${
                displayRank(tournament.pairing.pairingParams.handicap.rankThreshold)
            }"/>
            <PlacementParameterSet>
            <PlacementCriteria>
            ${
                (0..5).map {
                    """<PlacementCriterion name="${
                        tournament.pairing.placementParams.criteria.getOrNull(it)?.name ?: "NULL"
                    }" number="${it + 1}"/>"""
                }.joinToString("\n")
            }
            </PlacementCriteria>
            </PlacementParameterSet>
            <PairingParameterSet paiBaAvoidDuplGame="${
                tournament.pairing.pairingParams.base.dupWeight.toLong()
            }" paiBaBalanceWB="${
                tournament.pairing.pairingParams.base.colorBalanceWeight.toLong()
            }" paiBaDeterministic="${
                tournament.pairing.pairingParams.base.deterministic
            }" paiBaRandom="${
                tournament.pairing.pairingParams.base.random.toLong()
            }" paiMaAdditionalPlacementCritSystem1="${
                tournament.pairing.pairingParams.main.additionalPlacementCritSystem1.toString().titleCase()
            }" paiMaAdditionalPlacementCritSystem2="${
                tournament.pairing.pairingParams.main.additionalPlacementCritSystem2.toString().titleCase()
            }" paiMaAvoidMixingCategories="${
                tournament.pairing.pairingParams.main.categoriesWeight.toLong()
            }" paiMaCompensateDUDD="${
                tournament.pairing.pairingParams.main.compensateDrawUpDown
            }" paiMaDUDDLowerMode="${
                tournament.pairing.pairingParams.main.drawUpDownLowerMode.toString().substring(0, 3)
            }" paiMaDUDDUpperMode="${
                tournament.pairing.pairingParams.main.drawUpDownUpperMode.toString().substring(0, 3)
            }" paiMaDUDDWeight="${
                tournament.pairing.pairingParams.main.drawUpDownWeight.toLong()
            }" paiMaLastRoundForSeedSystem1="${
                tournament.pairing.pairingParams.main.lastRoundForSeedSystem1
            }" paiMaMaximizeSeeding="${
                tournament.pairing.pairingParams.main.seedingWeight.toLong()
            }" paiMaMinimizeScoreDifference="${
                tournament.pairing.pairingParams.main.scoreWeight.toLong()
            }" paiMaSeedSystem1="${
                tournament.pairing.pairingParams.main.seedSystem1.format()
            }" paiMaSeedSystem2="${
                tournament.pairing.pairingParams.main.seedSystem2.format()
            }" paiSeAvoidSameGeo="${
                tournament.pairing.pairingParams.geo.avoidSameGeo.toLong()
            }" paiSeBarThresholdActive="${
                tournament.pairing.pairingParams.secondary.barThresholdActive
            }" paiSeDefSecCrit="${
                tournament.pairing.pairingParams.secondary.defSecCrit.toLong()
            }" paiSeMinimizeHandicap="${
                tournament.pairing.pairingParams.handicap.weight.toLong()
            }" paiSeNbWinsThresholdActive="${
                tournament.pairing.pairingParams.secondary.nbWinsThresholdActive
            }" paiSePreferMMSDiffRatherThanSameClub="${
                tournament.pairing.pairingParams.geo.preferMMSDiffRatherThanSameClub
            }" paiSePreferMMSDiffRatherThanSameCountry="${
                tournament.pairing.pairingParams.geo.preferMMSDiffRatherThanSameCountry
            }" paiSeRankThreshold="${
                displayRank(tournament.pairing.pairingParams.secondary.rankSecThreshold).uppercase()
            }" paiStandardNX1Factor="${
                tournament.pairing.pairingParams.base.nx1
            }"/>
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
