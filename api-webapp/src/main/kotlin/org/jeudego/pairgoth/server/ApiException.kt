package org.jeudego.pairgoth.server

import com.republicate.kson.Json
import java.io.IOException

class ApiException : IOException {
    var code: Int
        private set
    var details: Json.Object
        private set

    constructor(code: Int) : super("error") {
        this.code = code
        details = Json.Object("message" to message)
    }

    constructor(code: Int, message: String?) : super(message) {
        this.code = code
        details = Json.Object("message" to message)
    }

    constructor(code: Int, cause: Exception) : super(cause) {
        this.code = code
        details = Json.Object("message" to "Erreur interne du serveur : " + cause.message)
    }

    constructor(code: Int, message: String, cause: Exception) : super(message, cause) {
        this.code = code
        details = Json.Object("message" to message + " : " + cause.message)
    }

    constructor(code: Int, details: Json.Object) : super(details.getString("message")) {
        this.code = code
        this.details = details
    }
}