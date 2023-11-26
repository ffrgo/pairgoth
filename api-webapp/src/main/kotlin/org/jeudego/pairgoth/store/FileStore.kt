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
import org.jeudego.pairgoth.model.toJson
import java.nio.file.Path
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.readText
import kotlin.io.path.useDirectoryEntries

private const val LEFT_PAD = 6 // left padding of IDs with '0' in filename
private fun Tournament<*>.filename() = "${id.toString().padStart(LEFT_PAD, '0')}-${shortName}.tour"

class FileStore(pathStr: String): StoreImplementation {
    companion object {
        private val filenameRegex = Regex("^(\\d+)-(.*)\\.tour$")
        private val timestampFormat: DateFormat = SimpleDateFormat("yyyyMMddHHmmss")
        private val timestamp: String get() = timestampFormat.format(Date())
    }

    private val path = Path.of(pathStr).also {
        val file = it.toFile()
        if (!file.mkdirs() && !file.isDirectory) throw Error("Property pairgoth.store.file.path must be a directory")
    }

    init {
        _nextTournamentId.set(getTournaments().keys.maxOrNull() ?: 0.toID())
    }

    override fun getTournaments(): Map<ID, String> {
        return path.useDirectoryEntries("*.tour") { entries ->
            entries.mapNotNull { entry ->
                val match = filenameRegex.matchEntire(entry.fileName.toString())
                match?.let { Pair(it.groupValues[1].toID(), it.groupValues[2]) }
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
        val players = json["players"] as Json.Array? ?: Json.Array()
        tournament.players.putAll(
            players.associate {
                (it as Json.Object).let { player ->
                    Pair(player.getID("id") ?: throw Error("invalid tournament file"), Player.fromJson(player))
                }
            }
        )
        if (tournament is TeamTournament) {
            val teams = json["teams"] as Json.Array? ?: Json.Array()
            tournament.teams.putAll(
                teams.associate {
                    (it as Json.Object).let { team ->
                        Pair(team.getID("id") ?: throw Error("invalid tournament file"), tournament.teamFromJson(team))
                    }
                }
            )
        }
        val games = json["games"] as Json.Array? ?: Json.Array()
        (1..games.size).forEach { round ->
            tournament.games(round).putAll(
                games.associate {
                    (it as Json.Object).let { game ->
                        Pair(game.getID("id") ?: throw Error("invalid tournament file"), Game.fromJson(game))
                    }
                }
            )
        }
        return tournament
    }

    override fun replaceTournament(tournament: Tournament<*>) {
        val filename = tournament.filename()
        val file = path.resolve(filename).toFile()
        if (file.exists()) {
            file.renameTo(path.resolve(filename + "-${timestamp}").toFile())
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
