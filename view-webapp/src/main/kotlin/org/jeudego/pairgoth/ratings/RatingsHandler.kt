package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

abstract class RatingsHandler(val origin: RatingsManager.Ratings) {

    private val delay = TimeUnit.HOURS.toMillis(1L)
    private val client = OkHttpClient()
    abstract val defaultURL: URL
    open val active = true
    val cacheFile = RatingsManager.path.resolve("${origin.name}.json").toFile()
    lateinit var players: Json.Array
    private var updated = false

    val url: URL by lazy {
        WebappManager.getProperty("ratings.${origin.name.lowercase(Locale.ROOT)}")?.let { URL(it) } ?: defaultURL
    }

    fun updateIfNeeded(): Boolean {
        return if (Date().time - cacheFile.lastModified() > delay) {
            RatingsManager.logger.info("Updating $origin cache from $url")
            val payload = fetchPayload()
            players = parsePayload(payload).also {
                val cachePayload = it.toString()
                cacheFile.printWriter().use { out ->
                    out.println(cachePayload)
                }
            }
            true
        } else if (!this::players.isInitialized) {
            players = Json.parse(cacheFile.readText())?.asArray() ?: Json.Array()
            true
        } else {
            false
        }
    }

    fun fetchPlayers(): Json.Array {
        updated = updateIfNeeded()
        return players
    }

    protected fun fetchPayload(): String {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Error("Could not fetch $origin ratings: unexpected code $response")
            val contentType = response.headers["Content-Type"]?.toMediaType()
            return response.body!!.source().readString(contentType?.charset() ?: defaultCharset())
        }
    }
    open fun defaultCharset() = StandardCharsets.UTF_8
    fun updated() = updated
    abstract fun parsePayload(payload: String): Json.Array
    val logger = LoggerFactory.getLogger(origin.name)
    val atom = "[-._`'a-zA-ZÀ-ÿ]"
}
