package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries

abstract class RatingsHandler(val origin: RatingsManager.Ratings) {
    companion object {
        private val delay = TimeUnit.HOURS.toMillis(1L)
        private val ymd = DateTimeFormatter.ofPattern("yyyyMMdd")
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    abstract val defaultURL: URL
    open val active = true
    lateinit var players: Json.Array
    lateinit var activeRatingsFile: File
    private var updated = false
    val ready get() = this::activeRatingsFile.isInitialized && this::players.isInitialized

    val url: URL by lazy {
        WebappManager.properties.getProperty("ratings.${origin.name.lowercase(Locale.ROOT)}")?.let { URL(it) } ?: defaultURL
    }

    fun activeDate() = ymd.parse(activeRatingsFile.name.substringBefore(".json").substringAfter("${origin.name}-"))

    fun getRatingsFiles() = RatingsManager.path.useDirectoryEntries("${origin.name}-*.json") { entries ->
        entries.sortedBy { it.fileName.name }.map {
            it.toFile()
        }.toList()
    }

    private fun getLatestRatingsFile() = getRatingsFiles().lastOrNull()

    private fun initIfNeeded(ratingsFile: File): Boolean {
        return if (!this::players.isInitialized) {
            readPlayers(ratingsFile)
            true
        } else false
    }

    @Synchronized
    private fun readPlayers(ratingsFile: File) {
        logger.info("Reading ${origin.name} players from ${ratingsFile.canonicalPath}")
        players = Json.parse(ratingsFile.readText())?.asArray() ?: Json.Array()
        activeRatingsFile = ratingsFile
    }

    @Synchronized
    fun updateIfNeeded(): Boolean {
        val latestRatingsFile = getLatestRatingsFile()
        if (latestRatingsFile != null && Date().time - latestRatingsFile.lastModified() < delay) {
            return initIfNeeded(latestRatingsFile)
        }
        val payload = fetchPayload()
        if (payload == null) {
            // an error occurred while fetching the payload, and has been reported
            if (latestRatingsFile != null) {
                // fall back to last ratings file
                return initIfNeeded(latestRatingsFile)
            } else {
                return false
            }
        }
        val (lastUpdated, lastPlayers) = parsePayload(payload)
        val ratingsFilename = "${origin.name}-${ymd.format(lastUpdated)}.json"
        if (latestRatingsFile != null && latestRatingsFile.name == ratingsFilename) {
            return initIfNeeded(latestRatingsFile)
        }
        RatingsManager.logger.info("Updating $origin cache from $url")
        activeRatingsFile = RatingsManager.path.resolve(ratingsFilename).toFile()
        activeRatingsFile.printWriter().use { out ->
            out.println(lastPlayers.toString())
        }
        players = lastPlayers
        return true
    }

    fun fetchPlayers(): Json.Array {
        updated = updateIfNeeded()
        return players
    }

    fun fetchPlayers(ratingsFile: File): Json.Array {
        initIfNeeded(ratingsFile)
        return players
    }

    protected fun fetchPayload(): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Could not fetch $origin ratings: unexpected code $response")
                val contentType = response.headers["Content-Type"]?.toMediaType()
                return response.body!!.source().readString(contentType?.charset() ?: defaultCharset())
            }
        } catch (ioe: IOException) {
            logger.error("Could not refresh ${origin.name} ratings from ${url}: ${ioe.javaClass.name} ${ioe.message}")
            logger.debug("Could not refresh ${origin.name} ratings from ${url}", ioe)
            return null
        }
    }
    open fun defaultCharset() = StandardCharsets.UTF_8
    fun updated() = updated
    abstract fun parsePayload(payload: String): Pair<LocalDate, Json.Array>
    val logger = LoggerFactory.getLogger(origin.name)
    val atom = "[-._`'a-zA-ZÀ-ÿ]"
}
