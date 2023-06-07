package org.jeudego.pairgoth.model

import com.republicate.kson.Json

typealias ID = Int

fun String.toID() = toInt()
fun String.toIDOrNull() = toIntOrNull()
fun Number.toID() = toInt()
fun Json.Object.getID(key: String) = getInt(key)
fun Json.Array.getID(index: Int) = getInt(index)