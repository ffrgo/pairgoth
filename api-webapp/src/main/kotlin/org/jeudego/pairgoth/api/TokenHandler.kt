package org.jeudego.pairgoth.api

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler.Companion.logger
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.Cryptograph
import org.jeudego.pairgoth.util.Randomizer
import org.jeudego.pairgoth.web.sharedSecret
import org.jeudego.pairgoth.web.toHex
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TokenHandler: ApiHandler {

    const val AUTH_HEADER = "Authorization"
    const val AUTH_PREFIX = "Bearer"

    private val hasher = MessageDigest.getInstance("SHA-256")
    private val cryptograph = AESCryptograph().apply { init(sharedSecret) }

    private data class AuthorizationPayload(
        val sessionId: String,
        val accessKey: String,
        val userInfos: Json
    )

    private fun parseAuthorizationHeader(request: HttpServletRequest): Pair<String, String>? {
        val authorize = request.getHeader(AUTH_HEADER) as String?
        logger.trace("Found authentication header: $authorize")
        if (authorize != null && authorize.startsWith("$AUTH_PREFIX ")) {
            val bearer = authorize.substring(AUTH_PREFIX.length + 1)
            val clear = cryptograph.webDecrypt(bearer)
            logger.trace("Decrypted bearer: $clear")
            val parts = clear.split(':')
            if (parts.size == 2) {
                return Pair(parts[0], parts[1])
            }
        }
        return null
    }

    private fun getAuthorizationPayload(request: HttpServletRequest): AuthorizationPayload? {
        parseAuthorizationHeader(request)?.let { (sessionId, accessKey) ->
            logger.trace("Session ID: $sessionId")
            logger.trace("Access key: $accessKey")
            val accessPayload = accesses.getIfPresent(accessKey)
            if (accessPayload != null && sessionId == accessPayload.getString("session")) {
                logger.trace("Found matching entry: $accessPayload")
                return AuthorizationPayload(sessionId, accessKey, accessPayload)
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
            val expectedSignature = hasher.digest(
                "${
                    session
                }:${
                    challenge
                }:${
                    email
                }".toByteArray(StandardCharsets.UTF_8)
            ).toHex()
            logger.trace("Expected signature: $expectedSignature")
            logger.trace("Received signature: $signature")
            if (signature == expectedSignature) {
                val accessKey = Randomizer.randomString(32)
                logger.trace("Generating access key: $accessKey")
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
            logger.trace("Invalidating access key for session id ${payload.sessionId} with user infos ${payload.userInfos}")
            accesses.invalidate(payload.accessKey)
        }
        return Json.Object("success" to true)
    }

    private fun failed(request: HttpServletRequest, response: HttpServletResponse) {
        val authValues = parseAuthorizationHeader(request)
        if (authValues != null && authValues.first.isNotEmpty()) {
            val sessionId = authValues.first
            val challenge = Randomizer.randomString(32)
            logger.trace("Generated challenge: $challenge")
            challenges.put(sessionId, challenge)
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
