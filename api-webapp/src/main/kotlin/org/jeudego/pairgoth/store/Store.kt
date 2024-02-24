package org.jeudego.pairgoth.store

import org.jeudego.pairgoth.server.WebappManager

private fun createStoreImplementation(): IStore {
    return when (val storeProperty = WebappManager.properties.getProperty("store") ?: "memory") {
        "memory" -> MemoryStore()
        "file" -> {
            val filePath = WebappManager.properties.getProperty("store.file.path") ?: "."
            FileStore(filePath)
        }
        else -> throw Error("unknown store: $storeProperty")
    }
}

object Store: IStore by createStoreImplementation()
