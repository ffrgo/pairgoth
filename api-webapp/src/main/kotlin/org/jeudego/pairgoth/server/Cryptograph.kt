package org.jeudego.pairgoth.server

import java.io.Serializable

/**
 * Cryptograph - used to encrypt and decrypt strings.
 *
 */
interface Cryptograph : Serializable {
    /**
     * init.
     * @param random random string
     */
    fun init(random: String)

    /**
     * encrypt.
     * @param str string to encrypt
     * @return encrypted string
     */
    fun encrypt(str: String): ByteArray

    /**
     * decrypt.
     * @param bytes to decrypt
     * @return decrypted string
     */
    fun decrypt(bytes: ByteArray): String
}
