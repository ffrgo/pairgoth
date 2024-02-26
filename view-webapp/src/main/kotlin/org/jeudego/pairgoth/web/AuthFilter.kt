package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jeudego.pairgoth.oauth.OauthHelperFactory
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.view.ApiTool
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.RequestDispatcher
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

class AuthFilter: Filter {

    private lateinit var filterConfig: FilterConfig

    protected val defaultRequestDispatcher: RequestDispatcher by lazy {
        filterConfig.servletContext.getNamedDispatcher("default")
    }

    override fun init(filterConfig: FilterConfig) {
        this.filterConfig = filterConfig
    }

    override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = resp as HttpServletResponse
        val uri = request.requestURI
        val session: HttpSession? = request.getSession(false)
        val auth = WebappManager.properties.getProperty("auth") ?: throw Error("authentication not configured")
        val forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null

        if (auth == "oauth" && uri.startsWith("/oauth/")) {
            val provider = uri.substring("/oauth/".length)
            val helper = OauthHelperFactory.getHelper(provider)
            val accessToken = helper.getAccessToken(request.session.id, request.getParameter("code") ?: "")
            val user = helper.getUserInfos(accessToken)
            handleSuccessfulLogin(req, user)
            request.session.setAttribute(SESSION_KEY_USER, user)
            response.sendRedirect("/index")
            return
        }

        if (auth == "none" || whitelisted(uri) || forwarded || session?.getAttribute(SESSION_KEY_USER) != null) {
            chain.doFilter(req, resp)
        } else {
            // TODO - protection against brute force attacks
            if (uri.endsWith("/index")) {
                response.sendRedirect("/index-ffg")
            } else {
                response.sendRedirect("/login")
            }
        }
    }

    companion object {
        const val SESSION_KEY_USER = "logged"
        const val SESSION_KEY_API_TOKEN = "pairgoth-api-token"

        private val logger = LoggerFactory.getLogger("auth")
        private val cryptograph = AESCryptograph().apply { init(sharedSecret) }
        private val hasher = MessageDigest.getInstance("SHA-256")
        private val client = OkHttpClient()

        private val whitelist = setOf(
            "/login",
            "/index-ffg",
            "/api/login",
            "api/logout"
        )

        fun whitelisted(uri: String): Boolean {
            if (uri.contains(Regex("\\.(?!html)"))) return true
            val nolangUri = uri.replace(Regex("^/../"), "/")
            return whitelist.contains(nolangUri)
        }

        fun handleSuccessfulLogin(req: HttpServletRequest, user: Json.Object) {
            logger.info("successful login for $user")
            req.session.setAttribute(SESSION_KEY_USER, user)
            fetchApiToken(req, user)?.also { token ->
                req.session.setAttribute(SESSION_KEY_API_TOKEN, token)
            }
        }

        fun fetchApiToken(req: HttpServletRequest, user: Json.Object): String? {
            try {
                logger.trace("getting challenge...")
                val challengeReq = Request.Builder().url("${ApiTool.apiRoot}tour/token")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${getBearer(req)}")
                    .build()
                val challengeResp = client.newCall(challengeReq).execute()
                challengeResp.use {
                    if (challengeResp.code == HttpServletResponse.SC_UNAUTHORIZED) {
                        logger.trace("building answer...")
                        val email = user.getString("email") ?: "-"
                        val challenge = challengeResp.headers["WWW-Authenticate"]
                        if (challenge != null) {
                            val signature = hasher.digest(
                                "${
                                    req.session.id
                                }:${
                                    challenge
                                }:${
                                    email
                                }".toByteArray(StandardCharsets.UTF_8)
                            ).toHex()
                            val answer = Json.Object(
                                "session" to req.session.id,
                                "email" to email,
                                "signature" to signature
                            )
                            val answerReq = Request.Builder().url("${ApiTool.apiRoot}tour/token")
                                .header("Accept", "application/json")
                                .post(answer.toString().toRequestBody(ApiTool.JSON.toMediaType()))
                                .build()
                            val answerResp = client.newCall(answerReq).execute()
                            answerResp.use {
                                if (answerResp.isSuccessful && "json" == answerResp.body?.contentType()?.subtype) {
                                    val payload = Json.parse(answerResp.body!!.string())
                                    if (payload != null && payload.isObject) {
                                        val token = payload.asObject().getString("token")
                                        if (token != null) {
                                            logger.trace("got token $token")
                                            return token
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                logger.warn("could not fetch access token", e)
            }
            return null
        }

        fun clearApiToken(req: HttpServletRequest) {
            val deleteTokenReq = Request.Builder().url("${ApiTool.apiRoot}tour/token").delete().build()
            client.newCall(deleteTokenReq).execute()
        }

        fun getBearer(req: HttpServletRequest): String {
            val session = req.session
            return cryptograph.webEncrypt(
                "${
                    session.id
                }:${
                    session.getAttribute(SESSION_KEY_API_TOKEN) ?: ""    
                }")
        }
    }
}
