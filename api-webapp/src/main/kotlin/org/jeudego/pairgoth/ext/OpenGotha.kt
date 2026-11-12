package org.jeudego.pairgoth.ext

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import org.apache.commons.text.StringEscapeUtils
import java.time.LocalDate
import org.jeudego.pairgoth.model.*
import org.jeudego.pairgoth.opengotha.TeamType
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

    // The criteria OpenGotha names otherwise than we do. Those it does not have travel under our
    // own name: it reads an unknown name as no criterion, and we read it back.
    private val criteria = mapOf(
        Criterion.NONE to "NULL",
        Criterion.CATEGORY to "CAT",
        Criterion.RANK to "Rank",
        Criterion.RATING to "Rating",
        Criterion.SOSWM1 to "SOSW-1",
        Criterion.SOSWM2 to "SOSW-2",
        Criterion.SOSMM1 to "SOSM-1",
        Criterion.SOSMM2 to "SOSM-2"
    )
    private val Criterion.ogName get() = criteria[this] ?: name
    private fun criterion(ogName: String) =
        criteria.entries.firstOrNull { it.value.equals(ogName, ignoreCase = true) }?.key ?: Criterion.valueOf(ogName.uppercase())

    // Team criteria have their own names; a team's score and SOS follow the pairing system
    private fun teamCriteria(type: PairingType) = mapOf(
        (if (type == PairingType.MAC_MAHON) Criterion.MMS else Criterion.NBW) to "TEAMP",
        (if (type == PairingType.MAC_MAHON) Criterion.SOSM else Criterion.SOSW) to "SOST",
        Criterion.BDW to "BDW",
        Criterion.BDW4 to "BDW4U",
        Criterion.BDW3 to "BDW3U",
        Criterion.BDW2 to "BDW2U",
        Criterion.BDW1 to "BDW1U",
        Criterion.RATING to "MNR"
    )
    // our teams have 5 boards at most: more upper boards are all of them
    private val allBoards = Regex("BDW[5-9]U")
    private val teamOnly = setOf(Criterion.BDW, Criterion.BDW4, Criterion.BDW3, Criterion.BDW2, Criterion.BDW1)

    // OpenGotha only knows players, their games, and where each player sits in each round: teams
    // are rebuilt from the seats, matches from the games between two teams.
    private fun importTeams(tournament: TeamTournament, ogTeams: List<TeamType>, players: Map<String, ID>, rounds: List<Collection<Game>>, byes: Map<Int, List<ID>>) {
        val size = tournament.type.playersNumber
        fun name(player: ID) = tournament.players[player]!!.fullName()
        val teamOf = mutableMapOf<ID, TeamTournament.Team>()
        // board of each player, per round; OpenGotha fills the seats of all its 20 rounds
        val seats = (1..tournament.rounds).associateWith { mutableMapOf<ID, Int>() }
        ogTeams.forEach { ogTeam ->
            val team = tournament.Team(nextPlayerId, ogTeam.teamName)
            tournament.teams[team.id] = team
            ogTeam.board.filter { it.roundNumber == null || it.roundNumber in seats.keys }.sortedBy { it.boardNumber }.forEach { board ->
                val player = players[board.player] ?: throw Error("player not found: ${board.player}")
                teamOf.put(player, team)?.takeIf { it !== team }?.let { other ->
                    throw Error("${name(player)} plays for both ${other.name} and ${team.name}: a player changing team cannot be imported")
                }
                team.playerIds.add(player)
                // before OpenGotha 3.28.04 a seat had no round, and held for all of them
                (board.roundNumber?.let { listOf(it) } ?: seats.keys).forEach { round -> seats[round]!![player] = board.boardNumber }
            }
        }
        // the members beyond the team's boards are substitutes for that round
        teamOf.keys.forEach { player ->
            seats.forEach { (round, seat) -> if ((seat[player] ?: Int.MAX_VALUE) > size) tournament.players[player]!!.skip.add(round) }
        }
        rounds.forEachIndexed { index, games ->
            val round = index + 1
            val seat = seats[round] ?: emptyMap()
            fun team(player: ID) = teamOf[player]?.id ?: throw Error("round $round: ${name(player)} plays without a team")
            val matches = games.groupBy { setOf(team(it.white), team(it.black)) }
            matches.keys.firstOrNull { it.size == 1 }?.let { throw Error("round $round: two players of ${tournament.teams[it.first()]!!.name} play each other") }
            matches.keys.flatten().groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
                throw Error("round $round: ${tournament.teams[it.key]!!.name} meets several teams")
            }
            val ordered = matches.values.map { boards ->
                boards.sortedWith(compareBy({ minOf(seat[it.white] ?: Int.MAX_VALUE, seat[it.black] ?: Int.MAX_VALUE) }, { it.table }))
            }.sortedBy { boards -> boards.minOf { it.table } }
            // our exports spread the boards of table t over the tables (t-1)*size+1 to t*size; otherwise matches are numbered in table order
            val tables = ordered.map { boards -> (boards.minOf { it.table } - 1) / size + 1 }
                .let { tables -> if (tables.distinct().size == tables.size) tables else tables.indices.map { it + 1 } }
            ordered.forEachIndexed { i, boards ->
                // white and black denote the first board's colours
                val match = Game(nextGameId, tables[i], team(boards.first().white), team(boards.first().black))
                boards.forEach { board ->
                    board.table = match.table
                    tournament.individualGames.put(match.id, board)
                }
                tournament.games(round)[match.id] = match
            }
            // a team has the bye when its players have it
            byes[round].orEmpty().mapNotNull { teamOf[it]?.id }.distinct().forEach { team ->
                if (tournament.games(round).values.none { it.white == team || it.black == team }) {
                    Game(id = nextGameId, table = 0, white = team, black = 0, result = Game.Result.WHITE).also { tournament.games(round)[it.id] = it }
                } else logger.warn("round $round: bye ignored, ${tournament.teams[team]!!.name} is playing")
            }
            tournament.propagateIndividualResults(round)
        }
    }

    // Where each member sits in a round: the boards as played, else as the pairing would field
    // them, then the other members, beyond the team's boards.
    private fun seating(tournament: TeamTournament, team: TeamTournament.Team, round: Int): List<Player> {
        val match = if (round > tournament.lastRound()) null else tournament.games(round).values.find { it.white == team.id || it.black == team.id }
        val playing = match?.let { tournament.individualGames[it.id] }?.mapNotNull { board ->
            listOf(board.white, board.black).firstOrNull { it in team.playerIds }?.let { tournament.players[it] }
        } ?: team.activePlayers(round)
        return playing + team.teamPlayers.filter { it !in playing }.sortedByDescending { it.rating }
    }

    private fun teams(tournament: TeamTournament) = tournament.teams.values.withIndex().joinToString("\n", "<Teams>\n", "\n</Teams>") { (index, team) ->
        (1..tournament.rounds).flatMap { round ->
            seating(tournament, team, round).mapIndexed { board, player ->
                """<Board boardNumber="${board + 1}" player="${player.key.escapeXML()}" roundNumber="$round"/>"""
            }
        }.joinToString("\n", """<Team teamName="${team.name.escapeXML()}" teamNumber="${index + 1}">""" + "\n", "\n</Team>")
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
                additionalPlacementCritSystem1 = criterion(pairParams.paiMaAdditionalPlacementCritSystem1),
                additionalPlacementCritSystem2 = criterion(pairParams.paiMaAdditionalPlacementCritSystem2),
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
                criterion(it.name)
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

        // a team tournament, in OpenGotha, is an individual one whose players sit in teams
        val ogTeams = ogTournament.teams?.team ?: emptyList()
        val type =
            if (ogTeams.isEmpty()) Tournament.Type.INDIVIDUAL
            else ogTournament.teamTournamentParameterSet?.teamGeneralParameterSet?.teamSize.let { size ->
                Tournament.Type.values().firstOrNull { it.individual && it.playersNumber == size }?.takeIf { it != Tournament.Type.INDIVIDUAL }
                    ?: throw Error("teams of $size players are not supported")
            }
        val placementParams =
            if (ogTeams.isEmpty()) pairgothPlacementParams
            else teamCriteria(pairingType).entries.associate { it.value to it.key }.let { ours ->
                PlacementParams(*ogTournament.teamTournamentParameterSet.teamPlacementParameterSet.placementCriteria.placementCriterion.filter {
                    it.name != "NULL"
                }.map {
                    ours[it.name] ?: if (allBoards.matches(it.name)) Criterion.BDW else criterion(it.name)
                }.distinct().toTypedArray())
            }

        val timeSystem = when (genParams.complementaryTimeSystem) {
            "SUDDENDEATH" -> SuddenDeath(genParams.basicTime * 60)
            "STDBYOYOMI" -> StandardByoyomi(genParams.basicTime * 60, genParams.stdByoYomiTime, 1) // no periods?
            "CANBYOYOMI" -> CanadianByoyomi(genParams.basicTime * 60, genParams.canByoYomiTime, genParams.nbMovesCanTime)
            "FISCHER" -> FischerTime(genParams.basicTime * 60, genParams.fischerTime)
            else -> throw Error("missing byoyomi type")
        }
        val pairing = when (pairingType) {
            PairingType.MAC_MAHON -> MacMahon(
                pairingParams = pairgothPairingParams,
                placementParams = placementParams,
                mmFloor = parseRankOrDefault(genParams.genMMFloor, -30, "MM floor"),
                mmBar = parseRankOrDefault(genParams.genMMBar, 0, "MM bar")
            )
            PairingType.FRENCH -> French(pairgothPairingParams, placementParams)
            PairingType.ROUND_ROBIN -> RoundRobin(pairgothPairingParams, placementParams)
            PairingType.SWISS -> Swiss(pairgothPairingParams, placementParams)
        }
        val online = genParams.isBInternet ?: false
        val gobanSize = genParams.size ?: 19
        val komi = genParams.komi?.toString()?.toDouble() ?: 7.5
        val tournament: Tournament<*> =
            if (type == Tournament.Type.INDIVIDUAL) StandardTournament(
                nextTournamentId, type, genParams.name, genParams.shortName, genParams.beginDate.toLocalDate(), genParams.endDate.toLocalDate(),
                genParams.director, country, genParams.location, online, timeSystem, genParams.numberOfRounds, pairing, rules, gobanSize, komi)
            else TeamTournament(
                nextTournamentId, type, genParams.name, genParams.shortName, genParams.beginDate.toLocalDate(), genParams.endDate.toLocalDate(),
                genParams.director, country, genParams.location, online, timeSystem, genParams.numberOfRounds, pairing, rules, gobanSize, komi)

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
        val byes = ogTournament.byePlayers?.byePlayer.orEmpty().groupBy({ it.roundNumber }) {
            canonicMap[it.player] ?: throw Error("player not found: ${it.player}")
        }
        if (tournament is TeamTournament) {
            importTeams(tournament, ogTeams, canonicMap, gamesPerRound.map { it.values }, byes)
        } else {
            gamesPerRound.forEachIndexed { index, games ->
                tournament.games(index + 1).putAll(games)
            }
            byes.forEach { (round, players) ->
                players.forEach { playerId ->
                    val game = Game(id = nextGameId, table = 0, white = playerId, black = 0, result = Game.Result.WHITE)
                    tournament.games(round)[game.id] = game
                }
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
        val players: Map<ID, Player> = if (tournament is TeamTournament && !tournament.type.individual) teamsAsPlayers(tournament) else tournament.players
        // teams of individual players: OpenGotha gets the players, their games and their seats
        val teamTournament = (tournament as? TeamTournament)?.takeIf { it.type.individual }
        val size = tournament.type.playersNumber
        // (round, table, game) — in OpenGotha each board of a match has its own table
        val games = (1..tournament.lastRound()).flatMap { round ->
            tournament.games(round).values.filter { it.black != 0 && it.white != 0 }.flatMap { game ->
                if (teamTournament == null) listOf(Triple(round, game.table, game))
                else teamTournament.individualGames[game.id].orEmpty().mapIndexed { board, boardGame ->
                    Triple(round, (game.table - 1) * size + board + 1, boardGame)
                }
            }
        }
        // (round, player) — the bye of a team is the bye of its players
        val byes = (1..tournament.lastRound()).flatMap { round ->
            tournament.games(round).values.filter { it.black == 0 || it.white == 0 }.flatMap { game ->
                val id = if (game.black == 0) game.white else game.black
                (teamTournament?.teams?.get(id)?.activePlayers(round) ?: listOfNotNull(players[id])).map { round to it }
            }
        }
        // board wins only exist for teams, in OpenGotha: its players are ranked on the other criteria
        val placement = tournament.pairing.placementParams.criteria.filterNot { teamTournament != null && it in teamOnly }
        // without teams, OpenGotha's defaults
        val teamPlacement = teamTournament?.let { teamCriteria(it.pairing.type) }?.let { theirs ->
            (0..5).map { tournament.pairing.placementParams.criteria.getOrNull(it)?.let { crit -> theirs[crit] ?: crit.ogName } ?: "NULL" }
        } ?: listOf("TEAMP", "BDW", "BDW3U", "BDW2U", "BDW1U", "MNR")
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
            ${games.joinToString("\n") { (round, table, game) ->
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
                        table
                    }" whitePlayer="${
                        players[game.white]!!.key.escapeXML()
                    }"/>"""
                }
            }
            </Games>
            <ByePlayers>
            ${byes.joinToString("\n") { (round, player) -> """<ByePlayer player="${player.key.escapeXML()}" roundNumber="$round"/>""" }}
            </ByePlayers>
            ${teamTournament?.let { teams(it) } ?: ""}
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
                        placement.getOrNull(it)?.ogName ?: "NULL"
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
                tournament.pairing.pairingParams.main.additionalPlacementCritSystem1.ogName
            }" paiMaAdditionalPlacementCritSystem2="${
                tournament.pairing.pairingParams.main.additionalPlacementCritSystem2.ogName
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
            <TeamGeneralParameterSet teamSize="${if (teamTournament == null) 4 else size}"/>
            <TeamPlacementParameterSet>
            <PlacementCriteria>
            ${teamPlacement.mapIndexed { i, name -> """<PlacementCriterion name="$name" number="${i + 1}"/>""" }.joinToString("\n")}
            </PlacementCriteria>
            </TeamPlacementParameterSet>
            </TeamTournamentParameterSet>
            </Tournament>
            
        """.trim() // inserted lines have no indent, trimIndent() would leave spaces before the XML declaration

        return xml
    }
}
