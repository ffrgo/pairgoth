package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.model.ID
import org.jeudego.pairgoth.model.Tournament
import java.util.concurrent.atomic.AtomicInteger

private fun createStoreImplementation(): StoreImplementation {
    val storeProperty = System.getProperty("pairgoth.store") ?: "memory"
    return when (storeProperty) {
        "memory" -> MemoryStore()
        "file" -> {
            val filePath = System.getProperty("pairgoth.store.file.path") ?: "."
            FileStore(filePath)
        }
        else -> throw Error("unknown store: $storeProperty")
    }
}

object Store: StoreImplementation by createStoreImplementation()
