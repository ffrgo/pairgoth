package org.jeudego.pairgoth.util

import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


/**
 * Basic AES encryption. Please note that it uses the ECB block mode, which has the advantage
 * to not require random bytes, thus providing some *persistence* for the encrypted data, but
 * at the expense of some security weaknesses. The purpose here is just to encrypt temporary
 * session ids in URLs, not to protect state secrets.
 */
class AESCryptograph : Cryptograph {

    override fun init(key: String) {
        val bytes = key.toByteArray(Charset.defaultCharset())
        if (bytes.size < 16) {
            throw Error("not enough secret bytes")
        }
        val secret: SecretKey = SecretKeySpec(bytes, 0, 16, ALGORITHM)
        try {
            encrypt.init(ENCRYPT_MODE, secret)
            decrypt.init(DECRYPT_MODE, secret)
        } catch (e: Exception) {
            throw RuntimeException("cyptograph initialization failed", e)
        }
    }

    override fun encrypt(str: String): ByteArray {
        return try {
            encrypt.doFinal(str.toByteArray(Charset.defaultCharset()))
        } catch (e: Exception) {
            throw RuntimeException("encryption failed failed", e)
        }
    }

    override fun decrypt(bytes: ByteArray): String {
        return try {
            String(decrypt.doFinal(bytes), Charset.defaultCharset())
        } catch (e: Exception) {
            throw RuntimeException("encryption failed failed", e)
        }
    }

    private var encrypt = Cipher.getInstance(CIPHER)
    private var decrypt = Cipher.getInstance(CIPHER)

    companion object {
        private val CIPHER = "AES/ECB/PKCS5Padding"
        private val ALGORITHM = "AES"
    }
}
