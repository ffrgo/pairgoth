package org.jeudego.pairgoth.oauth

object OauthHelperFactory {
    private val ffg: OAuthHelper = FFGHelper()
    private val facebook: OAuthHelper = FacebookHelper()
    private val google: OAuthHelper = GoogleHelper()
    private val instagram: OAuthHelper = InstagramHelper()
    private val twitter: OAuthHelper = TwitterHelper()
    fun getHelper(provider: String?): OAuthHelper {
        return when (provider) {
            "facebook" -> facebook
            "google" -> google
            "instagram" -> instagram
            "twitter" -> twitter
            else -> throw RuntimeException("wrong provider")
        }
    }
}