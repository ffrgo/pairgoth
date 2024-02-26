package org.jeudego.pairgoth.api

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.republicate.kson.Json
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.Cryptograph
import org.jeudego.pairgoth.util.Randomizer
import org.jeudego.pairgoth.web.sharedSecret
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TokenHandler: ApiHandler {

    const val AUTH_HEADER = "Authorization"
    const val AUTH_PREFIX = "Bearer"

    private val cryptograph = AESCryptograph().apply { init(sharedSecret) }

    private data class AuthorizationPayload(
        val sessionId: String,
        val accessKey: String,
        val userInfos: Json
    )

    private fun getAuthorizationPayload(request: HttpServletRequest): AuthorizationPayload? {
        val authorize = request.getHeader(AUTH_HEADER) as String?
        if (authorize != null && authorize.startsWith("$AUTH_PREFIX ")) {
            val bearer = authorize.substring(AUTH_PREFIX.length + 1)
            val clear = cryptograph.webDecrypt(bearer)
            val parts = clear.split(':')
            if (parts.size == 2) {
                val sessionId = parts[0]
                val accessKey = parts[1]
                val accessPayload = accesses.getIfPresent(accessKey)
                if (accessPayload != null && sessionId == accessPayload.getString("session")) {
                    return AuthorizationPayload(sessionId, accessKey, accessPayload)
                }
            }
        }
        return null
    }

    fun getLoggedUser(request: HttpServletRequest) = getAuthorizationPayload(request)?.userInfos

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        if (getLoggedUser(request) == null) {
            failed(request, response)
            return null
        } else {
            return Json.Object("success" to true)
        }
    }

    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val auth = getObjectPayload(request)
        val session = auth.getString("session")
        val challenge = challenges.getIfPresent(session)
        challenges.invalidate(session)
        if (challenge != null) {
            val email = auth.getString("email")
            val signature = auth.getString("signature")
            val expectedSignature = cryptograph.webEncrypt(
                "${
                    session
                }:${
                    challenge
                }:${
                    email
                }"
            )
            if (signature == expectedSignature) {
                val accessKey = Randomizer.randomString(32)
                accesses.put(accessKey, Json.Object(
                    "session" to session,
                    "email" to email
                ))
                return Json.Object("token" to accessKey)
            }
        }
        failed(request, response)
        return null
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        getAuthorizationPayload(request)?.let { payload ->
            accesses.invalidate(payload.accessKey)
        }
        return Json.Object("success" to true)
    }

    private fun failed(request: HttpServletRequest, response: HttpServletResponse) {
        val authPayload = getAuthorizationPayload(request)
        if (authPayload != null && authPayload.sessionId.isNotEmpty()) {
            val challenge = Randomizer.randomString(32)
            challenges.put(authPayload.sessionId, challenge)
            response.addHeader("WWW-Authenticate", challenge)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.writer.println(Json.Object("status" to "failed", "message" to "unauthorized"))
        } else {
            response.status = HttpServletResponse.SC_BAD_REQUEST
        }
    }

    // a short-lived cache for sessionid <--> challenge association
    private val challenges: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()

    // a long-lived cache for access key <--> user association
    private val accesses: Cache<String, Json.Object> = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.DAYS)
        .maximumSize(100)
        .build()

}
