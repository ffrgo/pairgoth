package org.jeudego.pairgoth.oauth

// In progress

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.WebappManager
//import com.republicate.modality.util.AESCryptograph
//import com.republicate.modality.util.Cryptograph
import org.apache.commons.codec.binary.Base64
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.ApiClient.JsonApiClient
import org.jeudego.pairgoth.util.Cryptograph
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

abstract class OAuthHelper {
    abstract val name: String
    abstract fun getLoginURL(sessionId: String?): String
    protected val clientId: String
        protected get() = WebappManager.getMandatoryProperty("oauth." + name + ".client_id")
    protected val secret: String
        protected get() = WebappManager.getMandatoryProperty("oauth." + name + ".secret")
    protected val redirectURI: String?
        protected get() = try {
            val uri: String = WebappManager.getMandatoryProperty("webapp.external.url") + "/oauth"
            URLEncoder.encode(uri, "UTF-8")
        } catch (uee: UnsupportedEncodingException) {
            logger.error("could not encode redirect URI", uee)
            null
        }

    protected fun getState(sessionId: String): String {
        return name + ":" + encrypt(sessionId)
    }

    fun checkState(state: String, expectedSessionId: String): Boolean {
        val foundSessionId = decrypt(state)
        return expectedSessionId == foundSessionId
    }

    protected abstract fun getAccessTokenURL(code: String): String?
    @Throws(IOException::class)
    fun getAccessToken(code: String): String {
        val json: Json.Object = Json.Object() // TODO - apiClient.get(getAccessTokenURL(code))
        return json.getString("access_token")!! // ?!
    }

    protected abstract fun getUserInfosURL(accessToken: String): String?

    @Throws(IOException::class)
    fun getUserEmail(accessToken: String): String {
        val json = getUserInfosURL(accessToken)?.let { JsonApiClient.get(it).asObject() }
        return json?.getString("email") ?: throw IOException("could not fetch email")
    }

    companion object {
        protected var logger: Logger = LoggerFactory.getLogger("oauth")
        private const val salt = "0efd28fb53cbac42"
        private val sessionIdCrypto: Cryptograph = AESCryptograph().apply {
            init(salt)
        }

        private fun encrypt(input: String): String {
            return Base64.encodeBase64URLSafeString(sessionIdCrypto.encrypt(input))
        }

        private fun decrypt(input: String): String {
            return sessionIdCrypto.decrypt(Base64.decodeBase64(input))
        }
    }
}