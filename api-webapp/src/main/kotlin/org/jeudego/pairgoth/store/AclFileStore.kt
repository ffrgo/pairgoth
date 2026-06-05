package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.toID
import java.nio.file.Path
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.path.useDirectoryEntries

private const val LEFT_PAD = 6
private val aclFilenameRegex = Regex("^(\\d+)-(.*)\\.tour$")

/**
 * External (EGC) auth store. The canonical store is the shared root [FileStore]; per-user access is an
 * ACL expressed as symlinks `NNNNNN-name.tour` (possibly dangling — access can be granted before the
 * tournament exists) in `{root}/{email}`. This store lists the user's symlinks as their index and gates
 * every id-operation on "a symlink for this id exists", then delegates to the root store. It never
 * creates or removes symlinks: the EGC site owns the ACL (and the id allocation).
 */
class AclFileStore(aclDirStr: String, private val root: FileStore): Store {
    private val aclDir = Path.of(aclDirStr)
    private val displayFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun pad(id: ID) = id.toString().padStart(LEFT_PAD, '0')

    // Is access granted? An ACL symlink for this id is present (its target may not exist yet — dangling).
    // Keyed by the id prefix, so the symlink's name part need not match the root file's shortName.
    private fun granted(id: ID): Boolean =
        aclDir.toFile().isDirectory && aclDir.useDirectoryEntries("${pad(id)}-*.tour") { it.any() }

    private fun requireAccess(id: ID) {
        if (!granted(id)) throw Error("no access to tournament #$id")
    }

    override fun getTournaments(): Map<ID, Map<String, String>> {
        if (!aclDir.toFile().isDirectory) return emptyMap()
        return aclDir.useDirectoryEntries("*.tour") { entries ->
            entries.mapNotNull { entry ->
                aclFilenameRegex.matchEntire(entry.fileName.toString())?.let { m ->
                    val id = m.groupValues[1].toID()
                    // always supply lastModified (templates render it under strict-reference Velocity);
                    // dangling (provisioned, not yet created) tournaments have no file → empty
                    val lastModified = if (root.exists(id)) displayFormat.format(Date(root.fileMTime(id))) else ""
                    id to mapOf("name" to m.groupValues[2], "lastModified" to lastModified)
                }
            }.sortedBy { it.first }.toMap()
        }
    }

    override fun getTournament(id: ID): Tournament<*>? {
        if (!granted(id) || !root.exists(id)) return null   // not granted, or a dangling (uncreated) symlink
        return root.getTournament(id)
    }

    override fun addTournament(tournament: Tournament<*>) {
        requireAccess(tournament.id)        // operators can only create ids the EGC site provisioned
        root.addTournament(tournament)
    }

    override fun replaceTournament(tournament: Tournament<*>, actionSlug: String?) {
        requireAccess(tournament.id)
        root.replaceTournament(tournament, actionSlug)
    }

    override fun deleteTournament(tournament: Tournament<*>) {
        requireAccess(tournament.id)
        root.deleteTournament(tournament)
    }

    override fun listHistory(id: ID) = if (granted(id)) root.listHistory(id) else emptyList()
    override fun snapshotAction(id: ID, snapshot: String) = if (granted(id)) root.snapshotAction(id, snapshot) else null
    override fun restore(id: ID, snapshot: String) = if (granted(id)) root.restore(id, snapshot) else null
}
