package org.jeudego.pairgoth.api

class AuthChallenge {
    companion object {
        private val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private fun randomString(length: Int) = CharArray(length) { validChars.random() }.concatToString()
        private val lifespan = 30000L
    }
    private val _value = randomString(64)
    private val _gen = System.currentTimeMillis()

    val value get() =
        if (System.currentTimeMillis() - _gen > lifespan) null
        else _value
}
