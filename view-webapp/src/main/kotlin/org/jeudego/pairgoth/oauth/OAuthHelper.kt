package org.jeudego.pairgoth.oauth

// In progress

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.WebappManager
//import com.republicate.modality.util.AESCryptograph
//import com.republicate.modality.util.Cryptograph
import org.apache.commons.codec.binary.Base64
import org.apache.http.NameValuePair
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
    protected open val clientId: String
        get() = WebappManager.getMandatoryProperty("oauth.$name.client_id")
    protected val secret: String
        get() = WebappManager.getMandatoryProperty("oauth.$name.secret")
    protected open val redirectURI: String
        get() = WebappManager.getMandatoryProperty("webapp.external.url").removeSuffix("/") + "/oauth/${name}"

    protected fun getState(sessionId: String): String {
        return name + ":" + encrypt(sessionId)
    }

    fun checkState(state: String, expectedSessionId: String): Boolean {
        val foundSessionId = decrypt(state)
        return expectedSessionId == foundSessionId
    }

    protected abstract fun getAccessTokenURL(code: String): Pair<String, List<NameValuePair>>

    @Throws(IOException::class)
    fun getAccessToken(code: String): String {
        val (url, params) = getAccessTokenURL(code)
        val json = JsonApiClient.post(url, null, *params.toTypedArray()).asObject()
        return json.getString("access_token") ?: throw IOException("could not get access token")
    }

    protected abstract fun getUserInfosURL(accessToken: String): Pair<String, List<NameValuePair>>

    @Throws(IOException::class)
    fun getUserInfos(accessToken: String): Json.Object {
        val (url, params) = getUserInfosURL(accessToken)
        return JsonApiClient.get(url, *params.toTypedArray()).asObject()
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