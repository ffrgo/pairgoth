package org.jeudego.pairgoth.oauth

import org.apache.http.NameValuePair
import org.jeudego.pairgoth.util.ApiClient.param
import java.net.URLEncoder

class FacebookHelper : OAuthHelper() {
    override val name: String
        get() = "facebook"

    override fun getLoginURL(sessionId: String?): String {
        return "https://www.facebook.com/v14.0/dialog/oauth?" +
                "client_id=" + clientId +
                "&redirect_uri=" + URLEncoder.encode(redirectURI, "UTF-8") +
                "&scope=email" +
                "&state=" + getState(sessionId!!)
    }

    override fun getAccessTokenURL(code: String): Pair<String, List<NameValuePair>> {
        return Pair(
            "https://graph.facebook.com/v14.0/oauth/access_token",
            listOf(
                param("client_id=", clientId),
                param("redirect_uri=", redirectURI),
                param("client_secret=", secret),
                param("code=", code)
            )
        )
    }

    override fun getUserInfosURL(accessToken: String): Pair<String, List<NameValuePair>> {
        return Pair(
            "https://graph.facebook.com/me?field=email&access_token=$accessToken",
            listOf()
        )
    }
}