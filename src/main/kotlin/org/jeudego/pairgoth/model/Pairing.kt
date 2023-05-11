package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Pairing.PairingType.*

// TODO - this is only an early draft

sealed class Pairing(val type: PairingType) {
    companion object {}
    enum class PairingType { SWISS, MACMAHON, ROUNDROBIN }
}

class Swiss(
    var method: Method,
    var firstRoundMethod: Method = method
): Pairing(SWISS) {
    enum class Method { FOLD, RANDOM, SLIP }
}

class MacMahon(
    var bar: Int = 0,
    var minLevel: Int = -30
): Pairing(MACMAHON) {
    val groups = mutableListOf<Int>()
}

class RoundRobin: Pairing(ROUNDROBIN)

// Serialization

fun Pairing.Companion.fromJson(json: Json.Object) = when (json.getString("type")?.let { Pairing.PairingType.valueOf(it) } ?: badRequest("missing pairing type")) {
    SWISS -> Swiss(
        method = json.getString("method")?.let { Swiss.Method.valueOf(it) } ?: badRequest("missing pairing method"),
        firstRoundMethod = json.getString("firstRoundMethod")?.let { Swiss.Method.valueOf(it) } ?: json.getString("method")!!.let { Swiss.Method.valueOf(it) }
    )
    MACMAHON -> MacMahon(
        bar = json.getInt("bar") ?: 0,
        minLevel = json.getInt("minLevel") ?: -30
    )
    ROUNDROBIN -> RoundRobin()
}

fun Pairing.toJson() = when (this) {
    is Swiss -> Json.Object("type" to type.name, "method" to method.name, "firstRoundMethod" to firstRoundMethod.name)
    is MacMahon -> Json.Object("type" to type.name, "bar" to bar, "minLevel" to minLevel)
    is RoundRobin -> Json.Object("type" to type.name)
}
