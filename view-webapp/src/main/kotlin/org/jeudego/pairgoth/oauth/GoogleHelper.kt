package org.jeudego.pairgoth.oauth

class GoogleHelper : OAuthHelper() {
    override val name: String
        get() = "google"

    override fun getLoginURL(sessionId: String?): String {
        return ""
    }

    override fun getAccessTokenURL(code: String): String? {
        return null
    }

    override fun getUserInfosURL(accessToken: String): String? {
        return null
    }
}