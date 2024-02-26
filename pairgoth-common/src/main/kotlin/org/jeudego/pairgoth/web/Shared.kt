package org.jeudego.pairgoth.web

import org.jeudego.pairgoth.util.Randomizer
import java.lang.RuntimeException


// a randomly generated secret shared by the API and View webapps
val sharedSecret: String by lazy {
    BaseWebappManager.properties.getProperty("auth.shared_secret") ?: when (BaseWebappManager.properties.getProperty("mode")) {
        "standalone" -> Randomizer.randomString(16)
        else -> when (BaseWebappManager.properties.getProperty("auth")) {
            "none" -> " ".repeat(16)
            else -> throw RuntimeException("missing property auth.shared_secret")
        }
    }.also {
        if (it.length != 16) throw RuntimeException("shared secret must be 16 ascii chars long")
    }
}
