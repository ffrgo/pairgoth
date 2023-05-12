package org.jeudego.pairgoth.oauth

class FacebookHelper : OAuthHelper() {
    override val name: String
        get() = "facebook"

    override fun getLoginURL(sessionId: String?): String {
        return "https://www.facebook.com/v14.0/dialog/oauth?" +
                "client_id=" + clientId +
                "&redirect_uri=" + redirectURI +
                "&scope=email" +
                "&state=" + getState(sessionId!!)
    }

    override fun getAccessTokenURL(code: String): String? {
        return "https://graph.facebook.com/v14.0/oauth/access_token?" +
                "client_id=" + clientId +
                "&redirect_uri=" + redirectURI +
                "&client_secret=" + secret +
                "&code=" + code
    }

    override fun getUserInfosURL(accessToken: String): String? {
        return "https://graph.facebook.com/me?" +
                "field=email" +
                "&access_token=" + accessToken
    }
}