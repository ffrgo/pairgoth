package org.jeudego.pairgoth.store

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.server.ApiServlet.Companion.USER_KEY
import org.jeudego.pairgoth.server.WebappManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServletRequest

internal val _nextTournamentId = AtomicInteger()
internal val _nextPlayerId = AtomicInteger()
internal val _nextGameId = AtomicInteger()

val nextTournamentId get() = _nextTournamentId.incrementAndGet()
val nextPlayerId get() = _nextPlayerId.incrementAndGet()
val nextGameId get() = _nextGameId.incrementAndGet()

// for tests
val lastPlayerId get() = _nextPlayerId.get()

interface Store {
    fun getTournaments(): Map<ID, Map<String, String>>
    fun addTournament(tournament: Tournament<*>)
    fun getTournament(id: ID): Tournament<*>?
    fun replaceTournament(tournament: Tournament<*>, actionSlug: String? = null)
    fun deleteTournament(tournament: Tournament<*>)
    /** Past snapshots of a tournament, newest first. Empty when the store keeps no history. */
    fun listHistory(id: ID): List<HistorySnapshot> = emptyList()
    /** The `lastAction` label stored inside a past snapshot (read lazily); null if absent/unsupported. */
    fun snapshotAction(id: ID, snapshot: String): String? = null
    /**
     * Restores a past [snapshot] as the current state, recorded as a new (undoable) "restore"
     * mutation. Returns the restored tournament, or null if unsupported / snapshot not found.
     */
    fun restore(id: ID, snapshot: String): Tournament<*>? = null
}

// FileStore is kept as a singleton per root path: its in-memory tournament cache must persist
// across requests (getStore is called on every request).
private val fileStores = ConcurrentHashMap<String, FileStore>()
private fun fileStore(rootPath: String) = fileStores.getOrPut(rootPath) { FileStore(rootPath) }

// external auth: a per-user ACL view over the shared root FileStore (canonical store stays the root,
// so cache / SSE / history / concurrency are shared across the operators of a tournament)
private val aclStores = ConcurrentHashMap<String, AclFileStore>()
private fun aclStore(rootPath: String, email: String) =
    aclStores.getOrPut("$rootPath/$email") { AclFileStore("$rootPath/$email", fileStore(rootPath)) }

fun getStore(request: HttpServletRequest): Store {
    val storeType = WebappManager.getMandatoryProperty("store")
    return when (val auth = WebappManager.getMandatoryProperty("auth")) {
        "none", "sesame" ->
            when (storeType) {
                "memory" -> MemoryStore
                "file" -> fileStore(WebappManager.properties.getProperty("store.file.path") ?: ".")
                else -> throw Error("invalid store type: $storeType")
            }
        "oauth" -> {
            if (storeType == "memory") throw Error("invalid store type for oauth: $storeType")
            var rootPath = WebappManager.properties.getProperty("store.file.path") ?: "."
            (request.getAttribute(USER_KEY) as Json.Object?)?.getString("email")?.also { email ->
                rootPath = "$rootPath/$email"
                Path.of(rootPath).toFile().mkdirs()
            }
            fileStore(rootPath)
        }
        "external" -> {
            if (storeType == "memory") throw Error("invalid store type for external: $storeType")
            val rootPath = WebappManager.properties.getProperty("store.file.path") ?: "."
            val email = (request.getAttribute(USER_KEY) as Json.Object?)?.getString("email")
                ?: throw Error("missing user email for external auth")
            aclStore(rootPath, email)
        }
        else -> throw Error("invalid auth: $auth")
    }
}
