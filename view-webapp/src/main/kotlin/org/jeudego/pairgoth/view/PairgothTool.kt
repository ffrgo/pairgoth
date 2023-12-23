package org.jeudego.pairgoth.view

import com.republicate.kson.Json

/**
 * Generic utilities
 */

class PairgothTool {
    public fun toMap(array: Json.Array) = array.map { ser -> ser as Json.Object }.associateBy { it.getLong("id")!! }
}