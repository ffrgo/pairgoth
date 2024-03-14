package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.io.File
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
    }
    private val client = OkHttpClient()
    abstract val defaultURL: URL
    open val active = true
    lateinit var players: Json.Array
    lateinit var activeRatingsFile: File
    private var updated = false

    val url: URL by lazy {
        WebappManager.properties.getProperty("ratings.${origin.name.lowercase(Locale.ROOT)}")?.let { URL(it) } ?: defaultURL
    }

    public fun getRatingsFiles() = RatingsManager.path.useDirectoryEntries("${origin.name}-*.json") { entries ->
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
    abstract fun parsePayload(payload: String): Pair<LocalDate, Json.Array>
    val logger = LoggerFactory.getLogger(origin.name)
    val atom = "[-._`'a-zA-ZÀ-ÿ]"
}
