package org.jeudego.pairgoth.oauth

import org.apache.http.NameValuePair

class InstagramHelper : OAuthHelper() {
    override val name: String
        get() = "instagram"

    override fun getLoginURL(sessionId: String?): String {
        return ""
    }

    override fun getAccessTokenURL(code: String): Pair<String, List<NameValuePair>> {
        return Pair("", listOf())
    }

    override fun getUserInfosURL(accessToken: String): Pair<String, List<NameValuePair>> {
        return Pair("", listOf())
    }
}