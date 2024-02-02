package org.jeudego.pairgoth.oauth

import org.apache.http.NameValuePair
import org.jeudego.pairgoth.util.ApiClient.header
import org.jeudego.pairgoth.util.ApiClient.param
import java.net.URLEncoder

class FFGHelper : OAuthHelper() {
    override val name: String
        get() = "ffg"

    override val clientId = "pairgoth"

    private val FFG_HOST = "https://testffg"

    override fun getLoginURL(sessionId: String?): String {
        return "$FFG_HOST/oauth2/entry_point.php/authorize?" +
                "client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectURI, "UTF-8") +
                "&scope=email"
    }

    override fun getAccessTokenURL(code: String): Pair<String, List<NameValuePair>> {
        return Pair(
            "$FFG_HOST/oauth2/entry_point.php/access_token",
            listOf(
                param("client_id", clientId),
                param("redirect_uri", redirectURI),
                param("client_secret", secret),
                param("code", code),
                param("grant_type", "authorization_code")
            )
        )
    }

    override fun getUserInfosURL(accessToken: String): Pair<String, List<NameValuePair>> {
        return Pair(
            "$FFG_HOST/oauth2/entry_point.php/user_info",
            listOf(header("Authorization", "Bearer $accessToken"))
        )
    }
}
