package org.jeudego.pairgoth.util

import org.apache.commons.codec.binary.Base64
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


    fun webEncrypt(str: String) = Base64.encodeBase64URLSafeString(encrypt(str))

    fun webDecrypt(str: String) = decrypt(Base64.decodeBase64(str))

}
