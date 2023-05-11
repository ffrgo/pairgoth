package org.jeudego.pairgoth.oauth

class InstagramHelper : OAuthHelper() {
    override val name: String
        get() = "instagram"

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