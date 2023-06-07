package org.jeudego.pairgoth.oauth

class TwitterHelper : OAuthHelper() {
    override val name: String
        get() = "twitter"

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