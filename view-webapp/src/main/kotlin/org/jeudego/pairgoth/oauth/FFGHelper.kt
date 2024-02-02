package org.jeudego.pairgoth.oauth

class FFGHelper : OAuthHelper() {
    override val name: String
        get() = "facebook"

    private val FFG_HOST = "https://testffg"

    override fun getLoginURL(sessionId: String?): String {
        return "$FFG_HOST/oauth2/entry_point.php/authorize?" +
                "client_id=" + clientId +
                "&redirect_uri=" + redirectURI +
                "&scope=email"
    }

    override fun getAccessTokenURL(code: String): String? {
        return "$FFG_HOST/oauth2/entry_point.php/access_token?" +
                "client_id=" + clientId +
                "&redirect_uri=" + redirectURI +
                "&client_secret=" + secret +
                "&code=" + code
    }

    override fun getUserInfosURL(accessToken: String): String? {
        return "$FFG_HOST/oauth2/entry_point.php/user_info?" +
                "field=email" +
                "&access_token=" + accessToken
    }
}
