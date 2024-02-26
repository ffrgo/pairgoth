package org.jeudego.pairgoth.util

object Randomizer {
    private val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    fun randomString(length: Int) = CharArray(length) { validChars.random() }.concatToString()
}
