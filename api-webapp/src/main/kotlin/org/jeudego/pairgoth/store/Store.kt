package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.server.WebappManager
import java.util.concurrent.atomic.AtomicInteger

private fun createStoreImplementation(): StoreImplementation {
    return when (val storeProperty = WebappManager.getProperty("store") ?: "memory") {
        "memory" -> MemoryStore()
        "file" -> {
            val filePath = WebappManager.getProperty("store.file.path") ?: "."
            FileStore(filePath)
        }
        else -> throw Error("unknown store: $storeProperty")
    }
}

object Store: StoreImplementation by createStoreImplementation()
