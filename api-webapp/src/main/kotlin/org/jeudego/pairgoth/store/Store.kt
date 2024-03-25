package org.jeudego.pairgoth.store

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.server.ApiServlet.Companion.USER_KEY
import org.jeudego.pairgoth.server.WebappManager
import java.nio.file.Path
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
    fun replaceTournament(tournament: Tournament<*>)
    fun deleteTournament(tournament: Tournament<*>)
}

fun getStore(request: HttpServletRequest): Store {
    val storeType = WebappManager.getMandatoryProperty("store")
    return when (val auth = WebappManager.getMandatoryProperty("auth")) {
        "none", "sesame" ->
            when (storeType) {
                "memory" -> MemoryStore
                "file" -> {
                    val filePath = WebappManager.properties.getProperty("store.file.path") ?: "."
                    FileStore(filePath)
                }
                else -> throw Error("invalid store type: $storeType")
            }
        "oauth" -> {
            if (storeType == "memory") throw Error("invalid store type for oauth: $storeType")
            var rootPath = WebappManager.properties.getProperty("store.file.path") ?: "."
            (request.getAttribute(USER_KEY) as Json.Object?)?.getString("email")?.also { email ->
                rootPath = "$rootPath/$email"
                Path.of(rootPath).toFile().mkdirs()
            }
            FileStore(rootPath)
        }
        else -> throw Error("invalid auth: $auth")
    }
}
