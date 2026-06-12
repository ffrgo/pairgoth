package org.jeudego.pairgoth.web

import java.lang.RuntimeException


// the secret shared by the API and View webapps; in standalone mode the launcher generates it
// when absent (it cannot be generated here: each webapp classloader would get its own)
val sharedSecret: String by lazy {
    val secret = BaseWebappManager.properties.getProperty("auth.shared_secret")
        ?: when (BaseWebappManager.properties.getProperty("auth")) {
            "none" -> " ".repeat(16)
            else -> throw RuntimeException("missing property auth.shared_secret")
        }
    if (secret.length != 16) throw RuntimeException("shared secret must be 16 ascii chars long")
    secret
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
