package org.jeudego.pairgoth.store

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Game
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.getID
import org.jeudego.pairgoth.model.toFullJson
import org.jeudego.pairgoth.model.toID
import org.jeudego.pairgoth.server.WebappManager
import java.lang.Integer.max
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.useDirectoryEntries
import kotlin.io.path.walk

private const val LEFT_PAD = 6 // left padding of IDs with '0' in filename
private fun Tournament<*>.filename() = "${id.toString().padStart(LEFT_PAD, '0')}-${shortName}.tour"

class FileStore(pathStr: String): Store {
    companion object {
        private val filenameRegex = Regex("^(\\d+)-(.*)\\.tour$")
        private val displayFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        private val timestampFormat: DateFormat = SimpleDateFormat("yyyyMMddHHmmss")
        private val timestamp: String get() = timestampFormat.format(Date())

        @OptIn(ExperimentalPathApi::class)
        private fun getMaxID(): ID {
            val rootPath = Path.of(WebappManager.properties.getProperty("store.file.path") ?: ".")
            val globMatcher = PathMatcher { path -> path.fileName.toString().endsWith(".tour") }
            return rootPath.walk().filter { path -> globMatcher.matches(path) }.mapNotNull { path ->
                val match = filenameRegex.matchEntire(path.fileName.toString())
                match?.let { it.groupValues[1].toID() }
            }.maxOrNull() ?: 0
        }
        init {
            _nextTournamentId.set(getMaxID())
        }
    }

    private val path = Path.of(pathStr).also {
        val file = it.toFile()
        if (!file.mkdirs() && !file.isDirectory) throw Error("Property pairgoth.store.file.path must be a directory")
    }


    private fun lastModified(path: Path) = displayFormat.format(Date(path.toFile().lastModified()))


    override fun getTournaments(): Map<ID, Map<String, String>> {
        return path.useDirectoryEntries("*.tour") { entries ->
            entries.mapNotNull { entry ->
                val match = filenameRegex.matchEntire(entry.fileName.toString())
                match?.let { Pair(it.groupValues[1].toID(), mapOf(
                    "name" to it.groupValues[2],
                    "lastModified" to lastModified(entry))
                ) }
            }.sortedBy { it.first }.toMap()
        }
    }

    override fun addTournament(tournament: Tournament<*>) {
        val filename = tournament.filename()
        val file = path.resolve(filename).toFile()
        if (file.exists()) throw Error("File $filename already exists")
        val json = tournament.toFullJson()
        file.printWriter().use { out ->
            out.println(json.toPrettyString())
        }
    }

    override fun getTournament(id: ID): Tournament<*>? {
        val file = path.useDirectoryEntries("${id.toString().padStart(LEFT_PAD, '0')}-*.tour") { entries ->
            entries.map { entry ->
                entry.fileName.toString()
            }.firstOrNull() ?: throw Error("no such tournament")
        }
        val json = Json.parse(path.resolve(file).readText())?.asObject() ?: throw Error("could not read tournament")
        val tournament = Tournament.fromJson(json)
        var maxPlayerId = 0
        var maxGameId = 0
        val players = json["players"] as Json.Array? ?: Json.Array()
        tournament.players.putAll(
            players.associate {
                (it as Json.Object).let { player ->
                    Pair(player.getID("id") ?: throw Error("invalid tournament file"), Player.fromJson(player)).also {
                        maxPlayerId = max(maxPlayerId, it.first)
                    }
                }
            }
        )
        if (tournament is TeamTournament) {
            val teams = json["teams"] as Json.Array? ?: Json.Array()
            tournament.teams.putAll(
                teams.associate {
                    (it as Json.Object).let { team ->
                        Pair(team.getID("id") ?: throw Error("invalid tournament file"), tournament.teamFromJson(team)).also {
                            maxPlayerId = max(maxPlayerId, it.first)
                        }
                    }
                }
            )
        }
        val games = json["games"] as Json.Array? ?: Json.Array()
        (1..games.size).forEach { round ->
            var nextDefaultTable = 1;
            val roundGames = games[round - 1] as Json.Array
            tournament.games(round).putAll(
                roundGames.associate {
                    (it as Json.Object).let { game ->
                        val fixedGame =
                            if (game.containsKey("t")) game
                            else Json.MutableObject(game).set("t", nextDefaultTable++)
                        Pair(game.getID("id") ?: throw Error("invalid tournament file"), Game.fromJson(fixedGame)).also {
                            maxGameId = max(maxGameId, it.first)
                        }
                    }
                }
            )
        }
        _nextPlayerId.set(maxPlayerId + 1)
        _nextGameId.set(maxGameId + 1)
        return tournament
    }

    override fun replaceTournament(tournament: Tournament<*>) {
        val filename = tournament.filename()
        // short name may have changed
        path.useDirectoryEntries("${tournament.id.toString().padStart(LEFT_PAD, '0')}-*.tour") { entries ->
            entries.mapNotNull { entry ->
                entry.toFile()
            }.firstOrNull()
        }?.let { file ->
            val dest = path.resolve(filename + "-${timestamp}").toFile()
            if (dest.exists()) {
                // it means the user performed several actions in the same second...
                // drop the last occurrence
                dest.delete()
            }
            if (!file.renameTo(dest)) {
                throw Error("Cannot rename ${file.path} to ${dest.path}")
            }
        }

        addTournament(tournament)
    }

    override fun deleteTournament(tournament: Tournament<*>) {
        val filename = tournament.filename()
        val file = path.resolve(filename).toFile()
        if (!file.exists()) throw Error("File $filename does not exist")
        file.renameTo(path.resolve(filename + "-${timestamp}").toFile())
    }
}
